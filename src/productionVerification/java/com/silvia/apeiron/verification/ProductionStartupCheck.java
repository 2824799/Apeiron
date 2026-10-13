package com.silvia.apeiron.verification;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.launchwrapper.Launch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Runs only in an isolated installed-game profile, with the actual distribution jar and unmapped dependencies. */
@Mod(
    modid = "apeiron_production_verification",
    name = "Apeiron Release Verification",
    version = "1",
    dependencies = "required-after:apeiron")
public final class ProductionStartupCheck {

    private int checked;

    @Mod.EventHandler
    public void loaded(FMLLoadCompleteEvent event) throws Exception {
        if (!Boolean.FALSE.equals(Launch.blackboard.get("fml.deobfuscatedEnvironment")))
            throw new IllegalStateException("Release verification requires an installed, obfuscated game environment");
        ClassLoader loader = getClass().getClassLoader();
        boolean checkInterfaceRows = false;
        Class<?> pluginClass = Class.forName("com.silvia.apeiron.mixin.config.ApeironMixinConfigPlugin", true, loader);
        Object plugin = pluginClass.getConstructor()
            .newInstance();
        JsonObject config;
        try (InputStreamReader reader = new InputStreamReader(
            loader.getResourceAsStream("mixins.apeiron.json"),
            StandardCharsets.UTF_8)) {
            config = new JsonParser().parse(reader)
                .getAsJsonObject();
        }
        for (String section : new String[] { "mixins", "client" })
            for (JsonElement entry : config.getAsJsonArray(section)) {
                String mixin = config.get("package")
                    .getAsString() + "."
                    + entry.getAsString();
                ClassNode node = new ClassNode();
                try (InputStream stream = loader.getResourceAsStream(mixin.replace('.', '/') + ".class")) {
                    new ClassReader(stream).accept(node, ClassReader.SKIP_CODE);
                }
                List<AnnotationNode> annotations = new ArrayList<>();
                if (node.visibleAnnotations != null) annotations.addAll(node.visibleAnnotations);
                if (node.invisibleAnnotations != null) annotations.addAll(node.invisibleAnnotations);
                for (AnnotationNode annotation : annotations) {
                    if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
                    for (int i = 0; i < annotation.values.size(); i += 2) {
                        if (!annotation.values.get(i)
                            .equals("value")
                            && !annotation.values.get(i)
                                .equals("targets"))
                            continue;
                        for (Object target : (List<?>) annotation.values.get(i + 1)) {
                            String name = target instanceof Type ? ((Type) target).getClassName() : target.toString();
                            if (!(Boolean) pluginClass.getMethod("shouldApplyMixin", String.class, String.class)
                                .invoke(plugin, name, mixin)) continue;
                            Class.forName(name, false, loader)
                                .getDeclaredMethods();
                            if (mixin.endsWith(".GuiInterfaceTerminalRowsMixin")) checkInterfaceRows = true;
                            checked++;
                        }
                    }
                }
            }
        if (checkInterfaceRows) verifyInterfaceTerminalButton(loader);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    /** Invoke the shipped GUI's SRG button handler; development-only launches use a different method name. */
    private static void verifyInterfaceTerminalButton(ClassLoader loader) throws Exception {
        Class<?> mode = Class.forName("com.silvia.apeiron.client.gui.InterfaceTerminalFilterMode", true, loader);
        Object original = mode.getMethod("current")
            .invoke(null);
        Object all = java.lang.Enum.valueOf(mode.asSubclass(java.lang.Enum.class), "ALL");
        try {
            mode.getMethod("select")
                .invoke(all);
            Class<?> guiClass = Class.forName("appeng.client.gui.implementations.GuiInterfaceTerminal", true, loader);
            Object container = Class.forName("appeng.container.ContainerNull", true, loader)
                .getConstructor()
                .newInstance();
            Object gui = guiClass.getConstructor(Class.forName("net.minecraft.inventory.Container", false, loader))
                .newInstance(container);
            java.lang.reflect.Field field = guiClass.getDeclaredField("guiButtonHideFull");
            field.setAccessible(true);
            Object button = field.get(gui);
            if (!button.getClass()
                .getName()
                .equals("com.silvia.apeiron.client.gui.InterfaceTerminalFilterButton"))
                throw new IllegalStateException("Production interface terminal did not install the three-state filter");
            java.lang.reflect.Method action = guiClass
                .getDeclaredMethod("func_146284_a", Class.forName("net.minecraft.client.gui.GuiButton", false, loader));
            action.setAccessible(true);
            for (String expected : new String[] { "FREE_INTERFACES", "FREE_ROWS", "ALL" }) {
                action.invoke(gui, button);
                if (!((Enum<?>) mode.getMethod("current")
                    .invoke(null)).name()
                        .equals(expected))
                    throw new IllegalStateException(
                        "Production interface terminal filter did not cycle to " + expected);
            }
            System.out.println(
                "Production interface terminal filter verification passed: native SRG handler and all three modes");
        } finally {
            mode.getMethod("select")
                .invoke(original);
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) throws Exception {
        if (event.phase != TickEvent.Phase.END) return;
        ModContainer mod = Loader.instance()
            .getIndexedModList()
            .get("apeiron");
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(
                Files.readAllBytes(
                    mod.getSource()
                        .toPath()));
        StringBuilder hash = new StringBuilder();
        for (byte octet : digest) hash.append(String.format("%02x", octet & 255));
        Files.write(
            Paths.get("production-verification-passed.txt"),
            ("Installed release reached client tick after loading " + checked
                + " selected Mixin targets.\n"
                + mod.getVersion()
                + "\n"
                + hash
                + "  "
                + mod.getSource()
                    .getName()
                + "\n").getBytes(StandardCharsets.UTF_8));
        FMLCommonHandler.instance()
            .exitJava(0, false);
    }
}
