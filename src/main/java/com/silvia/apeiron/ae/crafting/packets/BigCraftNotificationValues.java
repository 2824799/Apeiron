package com.silvia.apeiron.ae.crafting.packets;

import java.math.BigInteger;

/** Constructor bridge for the legacy CraftNotification(long) API. */
public final class BigCraftNotificationValues {

    private static final ThreadLocal<BigInteger> NEXT = new ThreadLocal<>();

    private BigCraftNotificationValues() {}

    public static void capture(final BigInteger value) {
        NEXT.set(value);
    }

    public static BigInteger take(final long legacy) {
        final BigInteger value = NEXT.get();
        NEXT.remove();
        return value == null ? BigInteger.valueOf(legacy) : value;
    }
}
