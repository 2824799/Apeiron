package com.silvia.apeiron.ae.crafting.diagnostics;

import java.math.BigInteger;

/** Constructor bridge used while AE creates its legacy diagnostic row object. */
public final class BigDiagnosticRowValues {

    private static final ThreadLocal<BigInteger[]> NEXT = new ThreadLocal<>();

    private BigDiagnosticRowValues() {}

    public static void capture(final BigInteger totalProduced, final BigInteger elapsedTimeTicks,
        final BigInteger sampleCount) {
        NEXT.set(new BigInteger[] { totalProduced, elapsedTimeTicks, sampleCount });
    }

    public static BigInteger[] take() {
        final BigInteger[] values = NEXT.get();
        NEXT.remove();
        return values;
    }
}
