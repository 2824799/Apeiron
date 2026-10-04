package com.silvia.apeiron.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Bridges the old immediate-only notifications and the newer reason-based scheduler. */
public final class HatchNotifications {

    private static final ClassValue<Method> WATCHERS = new ClassValue<Method>() {

        @Override
        protected Method computeValue(Class<?> type) {
            for (Method method : type.getMethods()) {
                if (method.getName()
                    .equals("scheduleRecipeCheck") && method.getParameterTypes().length == 1) {
                    method.setAccessible(true);
                    return method;
                }
            }
            return optionalMethod(type, "scheduleRecipeCheckImmediate");
        }
    };

    private HatchNotifications() {}

    public static void schedule(Object watcher, boolean immediate) {
        Method method = WATCHERS.get(watcher.getClass());
        if (method == null) return; // beta-1 uses native periodic recipe checks.
        if (method.getParameterTypes().length == 0) invoke(method, watcher);
        else {
            Class<?> reason = method.getParameterTypes()[0];
            Object selected = null;
            for (Object value : reason.getEnumConstants()) {
                if (((Enum<?>) value).name()
                    .equals(immediate ? "IMMEDIATE" : "THROTTLED")) selected = value;
            }
            if (selected == null) throw new IllegalStateException("Unknown GregTech recipe notification API");
            invoke(method, watcher, selected);
        }
    }

    public static void registerSmartInput(MTEMultiBlockBase controller, IMetaTileEntity hatch) {
        Method method = optionalMethod(MTEMultiBlockBase.class, "addIfSmartInput", IMetaTileEntity.class);
        if (method != null) invoke(method, controller, hatch);
    }

    public static void notifyNative(MTEHatch hatch) {
        Method method = optionalMethod(MTEHatch.class, "notifyWatchers");
        if (method != null) invoke(method, hatch);
    }

    private static Method optionalMethod(Class<?> type, String name, Class<?>... parameters) {
        try {
            Method method = type.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException missing) {
            return null;
        }
    }

    private static void invoke(Method method, Object owner, Object... arguments) {
        try {
            method.invoke(owner, arguments);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("GregTech notification failed", cause);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot invoke GregTech notification", failure);
        }
    }
}
