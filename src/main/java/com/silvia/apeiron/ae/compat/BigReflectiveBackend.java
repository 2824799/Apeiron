package com.silvia.apeiron.ae.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Small reflection bridge for AE integrations whose optional mod APIs are not compile-time dependencies. */
public final class BigReflectiveBackend {

    private BigReflectiveBackend() {}

    public static Object field(final Object target, final String name) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                final Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (final NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (final ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot read integration field " + name, e);
            }
        }
        throw new IllegalStateException(
            "Missing integration field " + name
                + " on "
                + target.getClass()
                    .getName());
    }

    public static Object invoke(final Object target, final String name, final Object... args) {
        Method selected = null;
        Class<?> type = target.getClass();
        while (type != null && selected == null) {
            for (final Method method : type.getDeclaredMethods()) {
                if (method.getName()
                    .equals(name) && accepts(method.getParameterTypes(), args)) {
                    selected = method;
                    break;
                }
            }
            type = type.getSuperclass();
        }
        if (selected == null) {
            for (final Method method : target.getClass()
                .getMethods()) {
                if (method.getName()
                    .equals(name) && accepts(method.getParameterTypes(), args)) {
                    selected = method;
                    break;
                }
            }
        }
        if (selected == null) {
            throw new IllegalStateException(
                "Missing integration method " + name
                    + " on "
                    + target.getClass()
                        .getName());
        }
        try {
            selected.setAccessible(true);
            return selected.invoke(target, args);
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot invoke integration method " + name, e);
        }
    }

    public static int intValue(final Object value) {
        return ((Number) value).intValue();
    }

    public static long longValue(final Object value) {
        return ((Number) value).longValue();
    }

    public static boolean booleanValue(final Object value) {
        return (Boolean) value;
    }

    private static boolean accepts(final Class<?>[] types, final Object[] args) {
        if (types.length != args.length) return false;
        for (int i = 0; i < types.length; i++) {
            if (args[i] == null) continue;
            final Class<?> expected = wrap(types[i]);
            if (Number.class.isAssignableFrom(expected) && args[i] instanceof Number) continue;
            if (!expected.isInstance(args[i])) return false;
        }
        return true;
    }

    private static Class<?> wrap(final Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }
}
