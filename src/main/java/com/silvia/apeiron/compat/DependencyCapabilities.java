package com.silvia.apeiron.compat;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodNode;

/** Inspects dependency bytecode without loading targets before Mixin transforms them. */
public final class DependencyCapabilities {

    private static final Map<String, ClassNode> CLASSES = new HashMap<>();

    private DependencyCapabilities() {}

    public static synchronized ClassNode inspect(String name) {
        if (CLASSES.containsKey(name)) return CLASSES.get(name);
        ClassNode node = null;
        try (InputStream stream = DependencyCapabilities.class.getClassLoader()
            .getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (stream != null) {
                node = new ClassNode();
                new ClassReader(stream)
                    .accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot inspect dependency " + name, failure);
        }
        CLASSES.put(name, node);
        return node;
    }

    public static boolean hasClass(String name) {
        return inspect(name) != null;
    }

    public static boolean hasMethod(String owner, String name, String descriptor) {
        ClassNode node = inspect(owner);
        if (node == null) return false;
        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && (descriptor == null || method.desc.equals(descriptor))) return true;
        }
        return false;
    }
}
