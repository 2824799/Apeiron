package com.silvia.apeiron.ae.crafting.core;

import java.math.BigInteger;

/** Bridges exact server values into AE's legacy long-only CraftingCpuEntry constructor. */
public final class BigCraftingCpuEntries {

    private static final ThreadLocal<BigInteger[]> PENDING = new ThreadLocal<>();

    private BigCraftingCpuEntries() {}

    public static void capture(final BigInteger stored, final BigInteger active, final BigInteger pending) {
        PENDING.set(new BigInteger[] { stored, active, pending });
    }

    public static void captureSlot(final int slot, final BigInteger value) {
        BigInteger[] values = PENDING.get();
        if (values == null) {
            values = new BigInteger[] { BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO };
        }
        values[slot] = value;
        PENDING.set(values);
    }

    public static BigInteger[] take(final long stored, final long active, final long pending) {
        final BigInteger[] values = PENDING.get();
        PENDING.remove();
        return values == null
                ? new BigInteger[] {
                        BigInteger.valueOf(stored),
                        BigInteger.valueOf(active),
                        BigInteger.valueOf(pending) }
                : values;
    }
}
