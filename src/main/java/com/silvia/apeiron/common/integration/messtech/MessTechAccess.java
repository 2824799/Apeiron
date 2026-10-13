package com.silvia.apeiron.common.integration.messtech;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Optional MessTech APIs are resolved once per runtime class; no dependency types enter mandatory classes. */
public final class MessTechAccess {

    private static final ClassValue<Map<String, Field>> FIELDS = new ClassValue<Map<String, Field>>() {

        @Override
        protected Map<String, Field> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };
    private static final ClassValue<Map<String, Method>> METHODS = new ClassValue<Map<String, Method>>() {

        @Override
        protected Map<String, Method> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private static final ClassValue<Integer> PROCESSING = new ClassValue<Integer>() {

        @Override
        protected Integer computeValue(Class<?> type) {
            boolean mess = false;
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                String name = current.getName();
                if (name.equals("com.MessTech.common.machine.MTDBBFurnace")) return 2;
                if (name.equals("com.MessTech.common.machine.module.SpaceModuleInfinityBase")) return 3;
                if (name.equals("com.MessTech.common.machine.MTNanoScaleFoundry")) return 4;
                if (name.equals("com.MessTech.common.machine.BosesCraftingArray")) return 5;
                if (name.equals("com.MessTech.common.machine.Base.MTWirelessMultiMachineBase")) return 6;
                mess |= current.getName()
                    .startsWith("com.MessTech.common.machine.");
            }
            return mess ? 1 : 0;
        }
    };

    private MessTechAccess() {}

    public static boolean permitsSharedProcessing(Object machine) {
        int kind = PROCESSING.get(machine.getClass());
        if (kind == 0) return true;
        if (!com.silvia.apeiron.config.MixinFeature.MESSTECH.isEnabled()) return false;
        switch (kind) {
            case 1:
                return true;
            case 3:
                return machine instanceof MessModuleController;
            case 4:
                return machine instanceof MessNanoController;
            case 5:
                return machine instanceof MessNaniteController;
            case 6:
                return machine instanceof MessWirelessController;
            default:
                return false;
        }
    }

    public static Object get(Object target, String name) {
        try {
            return field(target.getClass(), name).get(target);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(name, error);
        }
    }

    public static void set(Object target, String name, Object value) {
        try {
            Field field = field(target.getClass(), name);
            field.set(target, convert(value, field.getType()));
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(name, error);
        }
    }

    private static Field field(Class<?> type, String name) {
        return FIELDS.get(type)
            .computeIfAbsent(name, key -> {
                for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                    try {
                        Field field = current.getDeclaredField(key);
                        field.setAccessible(true);
                        return field;
                    } catch (NoSuchFieldException missing) { /* Try the inherited native API. */ }
                }
                throw new IllegalStateException("Missing MessTech field: " + type.getName() + "." + key);
            });
    }

    public static Object call(Object target, String name, Object... arguments) {
        StringBuilder key = new StringBuilder(name);
        for (Object argument : arguments) key.append(':')
            .append(
                argument == null ? "null"
                    : argument.getClass()
                        .getName());
        Method method = METHODS.get(target.getClass())
            .computeIfAbsent(key.toString(), ignored -> {
                for (Class<?> current = target.getClass(); current != null; current = current.getSuperclass())
                    for (Method candidate : current.getDeclaredMethods()) {
                        Class<?>[] types = candidate.getParameterTypes();
                        if (!candidate.getName()
                            .equals(name) || types.length != arguments.length) continue;
                        boolean matches = true;
                        for (int i = 0; i < types.length; i++)
                            matches &= arguments[i] == null || types[i].isInstance(arguments[i])
                                || types[i].isPrimitive()
                                    && (arguments[i] instanceof Number || arguments[i] instanceof Boolean);
                        if (!matches) continue;
                        candidate.setAccessible(true);
                        return candidate;
                    }
                throw new IllegalStateException(
                    "Missing MessTech method: " + target.getClass()
                        .getName() + "." + name);
            });
        Object[] values = arguments.clone();
        for (int i = 0; i < values.length; i++) values[i] = convert(values[i], method.getParameterTypes()[i]);
        try {
            return method.invoke(target, values);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException(name, cause);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(name, error);
        }
    }

    private static Object convert(Object value, Class<?> type) {
        if (!(value instanceof Number)) return value;
        Number number = (Number) value;
        if (type == int.class) return number.intValue();
        if (type == long.class) return number.longValue();
        if (type == float.class) return number.floatValue();
        if (type == double.class) return number.doubleValue();
        if (type == short.class) return number.shortValue();
        if (type == byte.class) return number.byteValue();
        return value;
    }
}
