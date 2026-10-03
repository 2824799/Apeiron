package com.silvia.apeiron.ae.smoke;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.silvia.apeiron.Apeiron;

/** Checks native method selectors and their invocation targets against the actual release AE jar. */
public final class AEProductionEntrypointSmoke {

    private AEProductionEntrypointSmoke() {}

    public static void verify() {
        final String release = System.getenv("APEIRON_VERIFY_RELEASE_AE");
        final String prefix = "com/silvia/apeiron/mixin/ae/";
        final List<String> mixins = Arrays.asList(
            "crafting/core/ContainerCraftConfirmMixin",
            "crafting/gui/GuiCraftingTreeTaskBigMixin",
            "crafting/gui/GuiCraftingTreeBigMixin",
            "replenisher/ContainerSuperMEReplenisherBigMixin",
            "terminal/core/ContainerNetworkStatusBigMixin",
            "automation/ContainerLevelEmitterMixin",
            "automation/ContainerAdvancedLevelEmitterMixin",
            "terminal/core/GuiAmountMixin",
            "terminal/core/GuiCraftAmountMixin",
            "terminal/core/PatternBufferQuickMoveMixin",
            "terminal/pattern/GuiPatternValueAmountBigMixin",
            "terminal/level/GuiLevelEmitterBigMixin",
            "terminal/level/GuiAdvancedLevelEmitterBigMixin",
            "crafting/gui/GuiCraftingCPUTableBigMixin");
        verifyJar(release, prefix, mixins, true, "AE");
        verifyJar(
            System.getenv("APEIRON_VERIFY_RELEASE_GT"),
            "com/silvia/apeiron/mixin/gregtech/energy/",
            Arrays.asList("WirelessRecipeGuiMixin", "LegacyWirelessRecipeGuiMixin", "WirelessControllerStateMixin"),
            false,
            "GT display");
        verifyJar(
            System.getenv("APEIRON_VERIFY_RELEASE_OMNI"),
            "com/silvia/apeiron/mixin/compat/omniocular/",
            Arrays.asList("WirelessTooltipMixin"),
            false,
            "OmniOcular display");
    }

    private static void verifyJar(String release, String prefix, List<String> mixins, boolean nativeOnly,
        String label) {
        if (release == null || release.isEmpty()) return;
        final ClassLoader loader = AEProductionEntrypointSmoke.class.getClassLoader();
        int checked = 0;
        try (ZipFile jar = new ZipFile(release)) {
            for (String name : mixins) {
                ClassNode mixin = read(loader.getResourceAsStream(prefix + name + ".class"));
                final AnnotationNode definition = annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations)
                    .stream()
                    .filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))
                    .findFirst()
                    .get();
                final List<?> targets = (List<?>) value(definition, "value");
                final String targetName = targets == null ? ((List<?>) value(definition, "targets")).get(0)
                    .toString()
                    .replace('.', '/') : ((Type) targets.get(0)).getInternalName();
                final ZipEntry entry = jar.getEntry(targetName + ".class");
                // ModularUI has its own jar; this check only reads AE's production entrypoints.
                if (nativeOnly && entry == null && !targetName.startsWith("appeng/")) continue;
                if (entry == null) throw new IllegalStateException("Release target missing: " + targetName);
                final ClassNode target = read(jar.getInputStream(entry));
                for (MethodNode handler : mixin.methods) {
                    for (AnnotationNode injection : annotations(
                        handler.visibleAnnotations,
                        handler.invisibleAnnotations)) {
                        Object selectors = value(injection, "method");
                        if (!(selectors instanceof List)) continue;
                        final List<?> names = (List<?>) selectors;
                        if (nativeOnly && !name.contains("GuiCraftingTree")
                            && names.stream()
                                .noneMatch(selector -> isNativeSelector(selector.toString())))
                            continue;
                        final List<MethodNode> matched = new ArrayList<>();
                        for (MethodNode method : target.methods) {
                            if (names.stream()
                                .anyMatch(selector -> matches(selector.toString(), method))) matched.add(method);
                        }
                        if (matched.isEmpty())
                            throw new IllegalStateException(name + ": no release method matches " + names);
                        Object at = value(injection, "at");
                        final List<?> points = at instanceof List ? (List<?>) at : Arrays.asList(at);
                        for (Object point : points) {
                            if (!(point instanceof AnnotationNode)) continue;
                            AnnotationNode location = (AnnotationNode) point;
                            if (!"INVOKE".equals(value(location, "value"))) continue;
                            final String expected = (String) value(location, "target");
                            final Object ordinalValue = value(location, "ordinal");
                            final int ordinal = ordinalValue instanceof Integer ? (Integer) ordinalValue : -1;
                            int found = 0;
                            for (MethodNode method : matched) {
                                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                                    if (instruction instanceof MethodInsnNode) {
                                        final MethodInsnNode call = (MethodInsnNode) instruction;
                                        if (expected.equals("L" + call.owner + ";" + call.name + call.desc)) found++;
                                    }
                                }
                            }
                            if (found == 0 || ordinal >= found) throw new IllegalStateException(
                                name + ": release invocation missing: " + expected + " ordinal=" + ordinal);
                        }
                        checked++;
                    }
                }
            }
        } catch (Exception error) {
            throw new IllegalStateException(label + " production entrypoint verification failed", error);
        }
        Apeiron.LOG.info(
            "{} production method selectors and invocation targets verification passed: {} hooks",
            label,
            checked);
    }

    private static boolean isNativeSelector(String selector) {
        final String name = selector.split("\\(", 2)[0];
        return Arrays
            .asList(
                "detectAndSendChanges",
                "func_75142_b",
                "initGui",
                "func_73866_w_",
                "actionPerformed",
                "func_146284_a",
                "transferStackInSlot",
                "func_82846_b")
            .contains(name) || name.equals("drawFG");
    }

    private static boolean matches(String selector, MethodNode method) {
        return selector.equals(method.name) || selector.equals(method.name + method.desc);
    }

    private static ClassNode read(InputStream stream) throws java.io.IOException {
        if (stream == null) throw new java.io.IOException("Missing class resource");
        try (InputStream input = stream) {
            final ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        final List<AnnotationNode> result = new ArrayList<>();
        if (visible != null) result.addAll(visible);
        if (invisible != null) result.addAll(invisible);
        return result;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values == null) return null;
        for (int index = 0; index < annotation.values.size(); index += 2) {
            if (key.equals(annotation.values.get(index))) return annotation.values.get(index + 1);
        }
        return null;
    }
}
