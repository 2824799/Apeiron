package com.silvia.apeiron.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.silvia.apeiron.ae.storage.BigCellInventory;

import appeng.api.storage.ICellInventoryHandler;

/** Old AE2 cells have the same upgrade flags but no public policy getters. */
public final class CellUpgradePolicies {

    private static final ClassValue<Method[]> GETTERS = new ClassValue<Method[]>() {

        @Override
        protected Method[] computeValue(Class<?> type) {
            return new Method[] { find(type, "isDistribution"), find(type, "isOverflow") };
        }
    };

    private CellUpgradePolicies() {}

    public static boolean distribution(ICellInventoryHandler<?> handler) {
        Object cell = handler.getCellInv();
        if (cell instanceof BigCellInventory) return ((BigCellInventory) cell).hasDistributionCard();
        return policy(handler, 0);
    }

    public static boolean overflow(ICellInventoryHandler<?> handler) {
        Object cell = handler.getCellInv();
        if (cell instanceof BigCellInventory) return ((BigCellInventory) cell).hasOverflowCard();
        return policy(handler, 1);
    }

    private static Method find(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }

    private static boolean policy(Object handler, int index) {
        Method getter = GETTERS.get(handler.getClass())[index];
        if (getter == null) return false;
        try {
            return (Boolean) getter.invoke(handler);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("Cannot read AE cell policy", cause);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot read AE cell policy", failure);
        }
    }
}
