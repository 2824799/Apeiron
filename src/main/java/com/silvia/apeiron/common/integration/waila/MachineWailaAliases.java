package com.silvia.apeiron.common.integration.waila;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.FieldInsnNode;
import org.spongepowered.asm.lib.tree.LdcInsnNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.lib.tree.VarInsnNode;

/** Reads direct field-to-save-key mappings once per class; never executes a machine save or arbitrary getter. */
public final class MachineWailaAliases {

    private static final ClassValue<List<Alias>> ALIASES = new ClassValue<List<Alias>>() {

        @Override
        protected List<Alias> computeValue(Class<?> type) {
            List<Alias> result = new ArrayList<>();
            for (Class<?> owner = type; owner != null && owner != Object.class; owner = owner.getSuperclass())
                discover(owner, result);
            return result;
        }
    };

    private MachineWailaAliases() {}

    public static void write(Object machine, NBTTagCompound tag) {
        for (Alias alias : ALIASES.get(machine.getClass())) {
            // Native Waila and the most specific serializer take precedence over inherited aliases.
            if (tag.hasKey(alias.key)) continue;
            try {
                Object value = alias.field.get(machine);
                if (value == null) continue;
                if (value instanceof Enum<?>)
                    value = alias.kind.equals("Ljava/lang/String;") ? value.toString() : ((Enum<?>) value).ordinal();
                if (value instanceof FluidStack) {
                    if (!alias.fluidName) continue;
                    value = ((FluidStack) value).getFluid()
                        .getName();
                } else if (value instanceof Fluid) {
                    if (!alias.fluidName) continue;
                    value = ((Fluid) value).getName();
                }
                switch (alias.kind) {
                    case "Z":
                        if (value instanceof Boolean) tag.setBoolean(alias.key, (Boolean) value);
                        break;
                    case "B":
                        if (value instanceof Number) tag.setByte(alias.key, ((Number) value).byteValue());
                        break;
                    case "S":
                        if (value instanceof Number) tag.setShort(alias.key, ((Number) value).shortValue());
                        break;
                    case "I":
                        if (value instanceof Number) tag.setInteger(alias.key, ((Number) value).intValue());
                        break;
                    case "J":
                        if (value instanceof Number) tag.setLong(alias.key, ((Number) value).longValue());
                        break;
                    case "F":
                        if (value instanceof Number) tag.setFloat(alias.key, ((Number) value).floatValue());
                        break;
                    case "D":
                        if (value instanceof Number) tag.setDouble(alias.key, ((Number) value).doubleValue());
                        break;
                    case "Ljava/lang/String;":
                        if (value instanceof BigInteger && ((BigInteger) value).bitLength() < 4096)
                            value = value.toString();
                        if (value instanceof String && ((String) value).length() <= 4096)
                            tag.setString(alias.key, (String) value);
                        break;
                    case "[B":
                        if (value instanceof BigInteger && ((BigInteger) value).bitLength() < 4096)
                            tag.setByteArray(alias.key, ((BigInteger) value).toByteArray());
                        break;
                    default:
                        break;
                }
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Cannot read HUD alias " + alias.key, failure);
            }
        }
    }

    private static void discover(Class<?> owner, List<Alias> aliases) {
        try (InputStream bytes = owner.getResourceAsStream(
            "/" + owner.getName()
                .replace('.', '/') + ".class")) {
            if (bytes == null) return;
            ClassNode type = new ClassNode();
            new ClassReader(bytes).accept(type, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            for (MethodNode method : type.methods) {
                if ((!method.name.equals("saveNBTData") && !method.name.equals("writeToNBT"))
                    || !method.desc.equals("(Lnet/minecraft/nbt/NBTTagCompound;)V")) continue;
                for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction
                    != null; instruction = instruction.getNext()) {
                    if (!(instruction instanceof LdcInsnNode) || !(((LdcInsnNode) instruction).cst instanceof String))
                        continue;
                    AbstractInsnNode receiver = previous(instruction);
                    AbstractInsnNode object = next(instruction);
                    AbstractInsnNode value = next(object);
                    if (!local(receiver, 1) || !local(object, 0)
                        || !(value instanceof FieldInsnNode)
                        || value.getOpcode() != Opcodes.GETFIELD) continue;
                    Field field = field(owner, ((FieldInsnNode) value).name);
                    if (field == null) continue;
                    String key = (String) ((LdcInsnNode) instruction).cst;
                    boolean fluidName = false;
                    for (AbstractInsnNode operation = next(value); operation != null; operation = next(operation)) {
                        int opcode = operation.getOpcode();
                        if (field.getType() == BigInteger.class && opcode == Opcodes.BIPUSH
                            && ((org.spongepowered.asm.lib.tree.IntInsnNode) operation).operand == 10) continue;
                        if (opcode >= Opcodes.I2L && opcode <= Opcodes.I2S || opcode == Opcodes.CHECKCAST) continue;
                        if (!(operation instanceof MethodInsnNode)) break;
                        MethodInsnNode call = (MethodInsnNode) operation;
                        if (call.owner.equals("net/minecraft/nbt/NBTTagCompound")
                            && call.desc.startsWith("(Ljava/lang/String;")
                            && call.desc.endsWith(")V")) {
                            String kind = call.desc.substring("(Ljava/lang/String;".length(), call.desc.length() - 2);
                            if (key.length() <= 128) aliases.add(new Alias(key, kind, field, fluidName));
                            break;
                        }
                        // Only recognize conversions whose value can be reproduced from the field without invoking it.
                        if (field.getType()
                            .isEnum()
                            && (call.name.equals("name") || call.name.equals("toString")
                                || call.name.equals("ordinal")))
                            continue;
                        if (field.getType() == BigInteger.class && call.name.equals("toByteArray")) continue;
                        if (field.getType() == BigInteger.class && call.name.equals("toString")) continue;
                        if (Number.class.isAssignableFrom(field.getType()) && call.name.endsWith("Value")) continue;
                        if ((field.getType() == FluidStack.class || field.getType() == Fluid.class)
                            && (call.name.equals("getFluid") || call.name.equals("getName"))) {
                            fluidName = true;
                            continue;
                        }
                        break;
                    }
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot inspect HUD save keys for " + owner.getName(), failure);
        }
    }

    public static Field field(Class<?> type, String name) {
        for (Class<?> owner = type; owner != null; owner = owner.getSuperclass()) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException absent) {
                // Try the class that declares this inherited property.
            }
        }
        return null;
    }

    private static boolean local(AbstractInsnNode instruction, int index) {
        return instruction instanceof VarInsnNode && instruction.getOpcode() == Opcodes.ALOAD
            && ((VarInsnNode) instruction).var == index;
    }

    private static AbstractInsnNode next(AbstractInsnNode instruction) {
        if (instruction == null) return null;
        do {
            instruction = instruction.getNext();
        } while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }

    private static AbstractInsnNode previous(AbstractInsnNode instruction) {
        do {
            instruction = instruction.getPrevious();
        } while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }

    private static final class Alias {

        private final String key;
        private final String kind;
        private final Field field;
        private final boolean fluidName;

        private Alias(String key, String kind, Field field, boolean fluidName) {
            this.key = key;
            this.kind = kind;
            this.field = field;
            this.fluidName = fluidName;
        }
    }
}
