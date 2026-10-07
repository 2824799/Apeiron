package com.silvia.apeiron.verification;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.silvia.apeiron.config.MixinFeature;
import com.silvia.apeiron.mixin.config.ApeironMixinConfigPlugin;

/**
 * Fast preflight of every registered target and required method selector, without booting Minecraft.
 * Runtime transformation and behavioral tests remain the authority for instruction-level injection safety.
 */
public final class MixinContractCheck {

    private static final ClassLoader LOADER = MixinContractCheck.class.getClassLoader();
    private static final Map<String, ClassNode> CLASSES = new HashMap<>();

    private MixinContractCheck() {}

    public static void main(String[] args) throws Exception {
        List<String> report = new ArrayList<>(), failures = new ArrayList<>();
        report.add("mixin\tfeature\ttarget\thook\tselectors\trequired\tstatus");
        JsonObject config;
        try (Reader reader = new InputStreamReader(
            LOADER.getResourceAsStream("mixins.apeiron.json"),
            StandardCharsets.UTF_8)) {
            config = new JsonParser().parse(reader)
                .getAsJsonObject();
        }
        ApeironMixinConfigPlugin plugin = new ApeironMixinConfigPlugin();
        int selected = 0;
        for (String section : new String[] { "mixins", "client" }) {
            for (JsonElement entry : config.getAsJsonArray(section)) {
                String name = config.get("package")
                    .getAsString() + "."
                    + entry.getAsString();
                ClassNode mixin = read(name);
                if (mixin == null) throw new IllegalStateException("Missing registered mixin " + name);
                MixinFeature feature = MixinFeature.of(name);
                for (String target : targets(mixin)) {
                    if (!plugin.shouldApplyMixin(target, name)) {
                        report.add(name + "\t" + feature + "\t" + target + "\t-\t-\t-\tcapability-disabled");
                        continue;
                    }
                    selected++;
                    ClassNode type = read(target);
                    for (MethodNode method : mixin.methods) for (AnnotationNode annotation : annotations(
                        method.visibleAnnotations,
                        method.invisibleAnnotations)) {
                            String hook = Type.getType(annotation.desc)
                                .getClassName();
                            boolean overwrite = hook.equals("org.spongepowered.asm.mixin.Overwrite");
                            boolean shadow = hook.equals("org.spongepowered.asm.mixin.Shadow");
                            if (!overwrite && !shadow
                                && (!hook.startsWith("org.spongepowered.asm.mixin.injection.")
                                    || hook.endsWith(".Group")))
                                continue;
                            Object selectors = (overwrite || shadow)
                                ? method.name.replaceFirst("^shadow\\$", "") + method.desc
                                : value(annotation, "method");
                            if (selectors == null) continue;
                            int required = value(annotation, "require") instanceof Integer
                                ? (Integer) value(annotation, "require")
                                : 1;
                            if (required < 0) required = 1;
                            List<?> methods = selectors instanceof List ? (List<?>) selectors
                                : Collections.singletonList(selectors);
                            boolean exists = false;
                            for (Object selector : methods) exists |= hasMethod(type, selector.toString());
                            String status = exists ? "method-present" : required == 0 ? "optional" : "MISSING";
                            report.add(
                                name + "\t"
                                    + feature
                                    + "\t"
                                    + target
                                    + "\t"
                                    + hook.substring(hook.lastIndexOf('.') + 1)
                                    + ":"
                                    + method.name
                                    + "\t"
                                    + methods
                                    + "\t"
                                    + required
                                    + "\t"
                                    + status);
                            if (!exists && required > 0) failures.add(name + " -> " + target + " " + methods);
                            Object anchor = value(annotation, "at");
                            List<?> anchors = anchor instanceof List ? (List<?>) anchor
                                : Collections.singletonList(anchor);
                            for (Object point : anchors) {
                                if (!(point instanceof AnnotationNode at)) continue;
                                String kind = String.valueOf(value(at, "value"));
                                Object member = value(at, "target");
                                if (member == null || !(kind.equals("INVOKE") || kind.equals("FIELD"))) continue;
                                // Obfuscated Minecraft calls need the runtime refmap; these reports inspect dependency
                                // bytecode.
                                if (member.toString()
                                    .startsWith("Lnet/minecraft/")) continue;
                                boolean found = hasAnchor(type, methods, member.toString());
                                report.add(
                                    name + "\t"
                                        + feature
                                        + "\t"
                                        + target
                                        + "\tanchor:"
                                        + method.name
                                        + "\t"
                                        + member
                                        + "\t"
                                        + required
                                        + "\t"
                                        + (found ? "instruction-present" : "ANCHOR-MISSING"));
                                if (!found && required > 0)
                                    failures.add(name + " -> " + target + " " + methods + " missing anchor " + member);
                            }

                        }
                }
            }
        }
        Path output = Paths.get(args[0]);
        Files.createDirectories(output.getParent());
        Files.write(output, report, StandardCharsets.UTF_8);
        if (!failures.isEmpty())
            throw new IllegalStateException("Missing required target methods:\n" + String.join("\n", failures));
        System.out.println("Mixin contracts passed: " + selected + " selected targets; report " + output);
    }

    private static boolean hasAnchor(ClassNode type, List<?> selectors, String target) throws IOException {
        if (type == null) return false;
        for (MethodNode method : type.methods) {
            boolean selected = false;
            for (Object selector : selectors) {
                String text = selector.toString();
                selected |= text.equals(method.name) || text.equals(method.name + method.desc);
            }
            if (!selected) continue;
            for (org.objectweb.asm.tree.AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                    && target.equals("L" + call.owner + ";" + call.name + call.desc)) return true;
                if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode field
                    && target.equals("L" + field.owner + ";" + field.name + ":" + field.desc)) return true;
            }
        }
        return type.superName != null && hasAnchor(read(type.superName.replace('/', '.')), selectors, target);
    }

    private static boolean hasMethod(ClassNode type, String selector) throws IOException {
        if (type == null) return false;
        // A member selector may include an owner before its method name, but descriptor ';' characters do not.
        if (selector.startsWith("L") && selector.indexOf(';') < selector.indexOf('('))
            selector = selector.substring(selector.indexOf(';') + 1);
        int paren = selector.indexOf('(');
        String name = paren < 0 ? selector : selector.substring(0, paren);
        String descriptor = paren < 0 ? null : selector.substring(paren);
        String regex = Pattern.quote(name)
            .replace("*", "\\E.*\\Q");
        for (MethodNode method : type.methods)
            if (method.name.matches(regex) && (descriptor == null || descriptor.equals(method.desc))) return true;
        return type.superName != null && hasMethod(read(type.superName.replace('/', '.')), selector);
    }

    private static List<String> targets(ClassNode type) {
        List<String> result = new ArrayList<>();
        for (AnnotationNode annotation : annotations(type.visibleAnnotations, type.invisibleAnnotations)) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
            for (String key : new String[] { "value", "targets" }) {
                Object list = value(annotation, key);
                if (list instanceof List) for (Object target : (List<?>) list)
                    result.add(target instanceof Type ? ((Type) target).getClassName() : target.toString());
            }
        }
        if (result.isEmpty()) throw new IllegalStateException("No target: " + type.name);
        return result;
    }

    private static Object value(AnnotationNode annotation, String name) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2)
            if (name.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }

    private static List<AnnotationNode> annotations(List<AnnotationNode> first, List<AnnotationNode> second) {
        List<AnnotationNode> result = new ArrayList<>();
        if (first != null) result.addAll(first);
        if (second != null) result.addAll(second);
        return result;
    }

    private static ClassNode read(String name) throws IOException {
        if (CLASSES.containsKey(name)) return CLASSES.get(name);
        ClassNode result = null;
        try (InputStream stream = LOADER.getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (stream != null) {
                result = new ClassNode();
                new ClassReader(stream).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        CLASSES.put(name, result);
        return result;
    }
}
