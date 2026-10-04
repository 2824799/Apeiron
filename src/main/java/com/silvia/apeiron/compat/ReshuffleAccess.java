package com.silvia.apeiron.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import appeng.api.config.AccessRestriction;
import appeng.me.storage.MEInventoryHandler;

/** Retains the native restrictions where present; older AE2 has no separate reshuffle policy. */
public final class ReshuffleAccess {

    private static final Method GETTER = find();

    private ReshuffleAccess() {}

    private static Method find() {
        try {
            return MEInventoryHandler.class.getMethod("getReshuffleAccess");
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }

    public static AccessRestriction policy(MEInventoryHandler<?> handler) {
        if (GETTER == null) return AccessRestriction.READ_WRITE;
        try {
            return (AccessRestriction) GETTER.invoke(handler);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("Cannot read AE reshuffle policy", cause);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot read AE reshuffle policy", failure);
        }
    }
}
