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
                    for (MethodNode method : node.methods) {
                        inspect(name, method.visibleAnnotations, remaps, methods, fields, report, failures);
                        inspect(name, method.invisibleAnnotations, remaps, methods, fields, report, failures);
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
