package com.silvia.apeiron.ae.crafting.diagnostics;

import java.math.BigInteger;

/** Small thread-local bridge for constructors whose legacy signatures only accept long. */
public final class BigCraftingDiagnosticsValues {

    private static final ThreadLocal<BigInteger> TIMING = new ThreadLocal<>();
    private static final ThreadLocal<BigInteger> COMPLETED = new ThreadLocal<>();

    private BigCraftingDiagnosticsValues() {}

    public static void captureTiming(final BigInteger value) {
        TIMING.set(value);
    }

    public static BigInteger takeTiming(final long legacy) {
        final BigInteger value = TIMING.get();
        TIMING.remove();
        return value == null ? BigInteger.valueOf(legacy) : value;
    }

    public static void captureCompleted(final BigInteger value) {
        COMPLETED.set(value);
    }

    public static BigInteger takeCompleted(final long legacy) {
        final BigInteger value = COMPLETED.get();
        COMPLETED.remove();
        return value == null ? BigInteger.valueOf(legacy) : value;
    }
}
