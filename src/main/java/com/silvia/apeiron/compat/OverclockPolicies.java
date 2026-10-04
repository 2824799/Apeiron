package com.silvia.apeiron.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import gregtech.api.util.OverclockCalculator;

/** Calls the native voltage/tier-skip rule on either OverclockCalculator API. */
public final class OverclockPolicies {

    private static final Method VOLTAGE = find();

    private OverclockPolicies() {}

    private static Method find() {
        try {
            return OverclockCalculator.class.getMethod("getMaxAllowedRecipeEUt");
        } catch (NoSuchMethodException absent) {
            try {
                return OverclockCalculator.class.getMethod("getAllowedTierSkip");
            } catch (NoSuchMethodException unsupported) {
                throw new IllegalStateException("Unsupported GregTech overclock API", unsupported);
            }
        }
    }

    public static boolean allows(OverclockCalculator calculator, long recipeEUt) {
        if (recipeEUt < 0) return false;
        try {
            Object rule = VOLTAGE.invoke(calculator);
            return rule instanceof Boolean ? (Boolean) rule : recipeEUt <= (Long) rule;
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("GregTech voltage check failed", cause);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot check GregTech voltage", failure);
        }
    }
}
