package com.silvia.apeiron.ae.smoke;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.mixin.config.ApeironMixinConfigPlugin;

/** Loads all configured AE targets through the real Mixin transformer, including classes first used on world join. */
public final class AEMixinTargetSmoke {

    private AEMixinTargetSmoke() {}

    public static void verify() {
        final ClassLoader loader = AEMixinTargetSmoke.class.getClassLoader();
        final Set<String> targets = new LinkedHashSet<>();
        try (InputStream stream = loader.getResourceAsStream("mixins.apeiron.json")) {
            if (stream == null) throw new IOException("Missing Apeiron Mixin configuration");
            final JsonObject configuration = new JsonParser()
                .parse(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
            final String mixinPackage = configuration.get("package")
                .getAsString();
            final ApeironMixinConfigPlugin plugin = new ApeironMixinConfigPlugin();
            for (final String section : new String[] { "mixins", "client" }) {
                if (!configuration.has(section)) continue;
                if (section.equals("client") && cpw.mods.fml.common.FMLCommonHandler.instance()
                    .getSide()
                    .isServer()) continue;
                for (final JsonElement entry : configuration.getAsJsonArray(section)) {
                    final String name = entry.getAsString();

                    final String mixinName = mixinPackage + "." + name;
                    for (final String target : readTargets(loader, mixinName)) {
                        if (cpw.mods.fml.common.FMLCommonHandler.instance()
                            .getSide()
                            .isServer() && target.startsWith("appeng.client.")) continue;
                        if (plugin.shouldApplyMixin(target, mixinName)) targets.add(target);
                    }
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("Configured Mixin target discovery failed", error);
        }

        final List<String> failures = new ArrayList<>();
        for (final String target : targets) {
            try {
                final Class<?> type = Class.forName(target, false, loader);
                // Resolve signatures too: AEBaseTile performs this reflection when loading a tile from a chunk.
                if (cpw.mods.fml.common.FMLCommonHandler.instance()
                    .getSide()
                    .isClient()) type.getDeclaredMethods();
                type.getDeclaredConstructors();
            } catch (Throwable error) {
                if (error instanceof VirtualMachineError) throw (VirtualMachineError) error;
                if (error instanceof ThreadDeath) throw (ThreadDeath) error;
                failures.add(target);
                Apeiron.LOG.error("Configured Mixin target could not load: " + target, error);
            }
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("Configured Mixin target loading failed: " + String.join(", ", failures));
        }
        Apeiron.LOG.info(
            "All configured Mixin targets and reflective signatures verification passed: {} classes",
            targets.size());
    }

    private static Set<String> readTargets(final ClassLoader loader, final String mixinName) throws IOException {
        final ClassNode node = new ClassNode();
        try (InputStream stream = loader.getResourceAsStream(mixinName.replace('.', '/') + ".class")) {
            if (stream == null) throw new IOException("Missing Mixin bytecode: " + mixinName);
            new ClassReader(stream)
                .accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        final List<AnnotationNode> annotations = new ArrayList<>();
        if (node.visibleAnnotations != null) annotations.addAll(node.visibleAnnotations);
        if (node.invisibleAnnotations != null) annotations.addAll(node.invisibleAnnotations);
        final Set<String> targets = new LinkedHashSet<>();
        for (final AnnotationNode annotation : annotations) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
            for (int i = 0; i < annotation.values.size(); i += 2) {
                final String key = (String) annotation.values.get(i);
                if (!key.equals("value") && !key.equals("targets")) continue;
                for (final Object value : (List<?>) annotation.values.get(i + 1)) {
                    targets.add(value instanceof Type ? ((Type) value).getClassName() : (String) value);
                }
            }
        }
        if (targets.isEmpty()) throw new IOException("Mixin has no target: " + mixinName);
        return targets;
    }
}
