package com.silvia.apeiron.verification;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Checks the shipped refmap, including Minecraft anchors that development-only contracts cannot validate. */
public final class ReleaseMappingCheck {

    private ReleaseMappingCheck() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> methods = new HashMap<>(), fields = new HashMap<>();
        for (String line : Files.readAllLines(Paths.get(args[1]), StandardCharsets.UTF_8)) {
            String[] parts = line.split(" ");
            if (parts[0].equals("MD:")) methods.put(member(parts[1]) + parts[2], member(parts[3]) + parts[4]);
            if (parts[0].equals("FD:")) fields.put(member(parts[1]), member(parts[2]));
        }
        List<String> report = new ArrayList<>(), failures = new ArrayList<>();
        report.add("mixin\tanchor\tproductionMapping\tstatus");
        try (ZipFile jar = new ZipFile(args[0])) {
            JsonObject config = json(jar, "mixins.apeiron.json");
            JsonObject mappings = json(
                jar,
                config.get("refmap")
                    .getAsString()).getAsJsonObject("mappings");
            for (String section : new String[] { "mixins", "client" })
                for (JsonElement entry : config.getAsJsonArray(section)) {
                    String name = (config.get("package")
                        .getAsString() + "."
                        + entry.getAsString()).replace('.', '/');
                    ClassNode node = new ClassNode();
                    new ClassReader(jar.getInputStream(jar.getEntry(name + ".class")))
                        .accept(node, ClassReader.SKIP_CODE);
                    JsonObject remaps = mappings.getAsJsonObject(name);
                    List<String> targets = targets(node);
                    for (MethodNode method : node.methods) {
                        inspect(name, method.visibleAnnotations, remaps, methods, fields, report, failures);
                        inspect(name, method.invisibleAnnotations, remaps, methods, fields, report, failures);
                        inspectSelectors(name, targets, method.visibleAnnotations, remaps, methods, report, failures);
                        inspectSelectors(name, targets, method.invisibleAnnotations, remaps, methods, report, failures);
                    }
                }
        }
        Path reportPath = Paths.get(args[2]);
        Files.createDirectories(reportPath.getParent());
        Files.write(reportPath, report, StandardCharsets.UTF_8);
        if (!failures.isEmpty())
            throw new IllegalStateException("Unmapped release injection anchors:\n" + String.join("\n", failures));
        System.out.println("Release Minecraft anchor mappings passed: " + (report.size() - 1));
    }

    /** Mod GUI overrides also acquire SRG names. Checking only @At Minecraft anchors misses their selectors. */
    private static void inspectSelectors(String mixin, List<String> targets, List<AnnotationNode> annotations,
        JsonObject remaps, Map<String, String> mappings, List<String> report, List<String> failures) throws Exception {
        if (annotations == null) return;
        for (AnnotationNode annotation : annotations) {
            if (!annotation.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/")) continue;
            Object selected = value(annotation, "method");
            if (!(selected instanceof List<?>selectors)) continue;
            for (String target : targets) for (Object selector : selectors) {
                String text = selector.toString();
                String name = selectorName(text);
                ClassNode type = readClass(target);
                MethodNode method = findMethod(
                    type,
                    name,
                    text.indexOf('(') < 0 ? null : text.substring(text.indexOf('(')));
                if (method == null) continue;
                String expected = inheritedMapping(type, method.name, method.desc, mappings);
                if (expected == null || selectorName(expected).equals(name)) continue;
                String mappedName = selectorName(expected);
                boolean valid = false;
                for (Object alternative : selectors) {
                    String source = alternative.toString();
                    String actual = remaps != null && remaps.has(source) ? remaps.get(source)
                        .getAsString() : source;
                    valid |= selectorName(actual).equals(mappedName);
                }
                report.add(
                    mixin + "\tselector:"
                        + target
                        + ";"
                        + text
                        + "\t"
                        + mappedName
                        + "\t"
                        + (valid ? "mapped-selector" : "SELECTOR-MISSING"));
                if (!valid) failures.add(mixin + " selector " + text + " expected " + mappedName);
            }
        }
    }

    private static String inheritedMapping(ClassNode type, String name, String desc, Map<String, String> mappings)
        throws Exception {
        if (type == null) return null;
        String mapping = mappings.get("L" + type.name + ";" + name + desc);
        if (mapping != null) return mapping;
        return type.superName == null ? null : inheritedMapping(readClass(type.superName), name, desc, mappings);
    }

    private static MethodNode findMethod(ClassNode type, String name, String desc) throws Exception {
        if (type == null) return null;
        for (MethodNode method : type.methods)
            if (method.name.equals(name) && (desc == null || method.desc.equals(desc))) return method;
        return type.superName == null ? null : findMethod(readClass(type.superName), name, desc);
    }

    private static ClassNode readClass(String name) throws Exception {
        if (name.startsWith("java/") || name.startsWith("javax/") || name.startsWith("jdk/") || name.startsWith("sun/"))
            return null;
        try (java.io.InputStream stream = ReleaseMappingCheck.class.getClassLoader()
            .getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (stream == null) return null;
            ClassNode node = new ClassNode();
            try {
                new ClassReader(stream).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("Cannot inspect selector target " + name, error);
            }
            return node;
        }
    }

    private static List<String> targets(ClassNode mixin) {
        List<String> result = new ArrayList<>();
        List<AnnotationNode> annotations = new ArrayList<>();
        if (mixin.visibleAnnotations != null) annotations.addAll(mixin.visibleAnnotations);
        if (mixin.invisibleAnnotations != null) annotations.addAll(mixin.invisibleAnnotations);
        for (AnnotationNode annotation : annotations) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
            for (String key : new String[] { "value", "targets" }) {
                Object value = value(annotation, key);
                if (value instanceof List<?>list) for (Object target : list)
                    result.add(target instanceof Type ? ((Type) target).getInternalName() : target.toString());
            }
        }
        return result;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2)
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }

    private static String selectorName(String selector) {
        int signature = selector.indexOf('(');
        int owner = selector.indexOf(';');
        int start = selector.startsWith("L") && owner >= 0 && (signature < 0 || owner < signature) ? owner + 1 : 0;
        return selector.substring(start, signature < 0 ? selector.length() : signature);
    }

    private static void inspect(String mixin, Object value, JsonObject remaps, Map<String, String> methods,
        Map<String, String> fields, List<String> report, List<String> failures) {
        if (value instanceof List<?>list) {
            for (Object child : list) inspect(mixin, child, remaps, methods, fields, report, failures);
        } else if (value instanceof AnnotationNode annotation) {
            if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/At;") && annotation.values != null) {
                for (int i = 0; i < annotation.values.size(); i += 2) {
                    if (!annotation.values.get(i)
                        .equals("target")) continue;
                    String anchor = annotation.values.get(i + 1)
                        .toString();
                    String expected = methods.get(anchor);
                    int colon = anchor.indexOf(':');
                    if (expected == null && colon > 0) {
                        String field = fields.get(anchor.substring(0, colon));
                        if (field != null) expected = field + anchor.substring(colon);
                    }
                    if (expected == null || expected.equals(anchor)) continue;
                    String actual = remaps != null && remaps.has(anchor) ? remaps.get(anchor)
                        .getAsString() : anchor;
                    boolean valid = expected.equals(actual);
                    report.add(mixin + "\t" + anchor + "\t" + actual + "\t" + (valid ? "mapped" : "MISSING"));
                    if (!valid) failures.add(mixin + " " + anchor + " expected " + expected);
                }
            }
            inspect(mixin, annotation.values, remaps, methods, fields, report, failures);
        }
    }

    private static String member(String name) {
        int slash = name.lastIndexOf('/');
        return "L" + name.substring(0, slash) + ";" + name.substring(slash + 1);
    }

    private static JsonObject json(ZipFile jar, String name) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
            jar.getInputStream(jar.getEntry(name)),
            StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader)
                .getAsJsonObject();
        }
    }
}
