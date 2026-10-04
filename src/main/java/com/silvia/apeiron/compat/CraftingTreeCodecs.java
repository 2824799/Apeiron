package com.silvia.apeiron.compat;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.crafting.v2.CraftingTreeSerializer;
import io.netty.buffer.ByteBuf;

/** Older serializers write directly to the packet instead of building an object table. */
public final class CraftingTreeCodecs {

    private static final Method FINISH = find(CraftingTreeSerializer.class, "finalizeSerializer");
    private static final Method INITIALIZE = find(CraftingTreeSerializer.class, "initializeSerializer");

    private CraftingTreeCodecs() {}

    public static ByteBuf finish(CraftingTreeSerializer writer) throws IOException {
        return FINISH == null ? writer.getBuffer() : (ByteBuf) invoke(FINISH, writer);
    }

    public static void initialize(CraftingTreeSerializer reader) throws IOException {
        if (INITIALIZE != null) invoke(INITIALIZE, reader);
    }

    public static void notifyReplenisher(Object tile, IAEStack<?> stack, long amount, BaseActionSource source) {
        Method method = find(tile.getClass(), "postStorageChange", IAEStack.class, long.class, BaseActionSource.class);
        if (method == null) return; // Older replenishers notify through their wrapping MEMonitorHandler.
        try {
            invoke(method, tile, stack, amount, source);
        } catch (IOException failure) {
            throw new IllegalStateException("AE replenisher notification failed", failure);
        }
    }

    private static Method find(Class<?> type, String name, Class<?>... arguments) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, arguments);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException missing) {}
        }
        return null;
    }

    private static Object invoke(Method method, Object owner, Object... arguments) throws IOException {
        try {
            return method.invoke(owner, arguments);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IOException("AE tree codec failed", cause);
        } catch (ReflectiveOperationException failure) {
            throw new IOException("Cannot invoke AE tree codec", failure);
        }
    }
}
