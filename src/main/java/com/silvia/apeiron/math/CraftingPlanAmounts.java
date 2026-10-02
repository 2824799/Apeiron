package com.silvia.apeiron.math;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/** Computes the display ratio only after exact inventory arithmetic. */
public final class CraftingPlanAmounts {

    private CraftingPlanAmounts() {}

    public static float usedPercent(BigInteger used, BigInteger available, boolean infinite) {
        if (infinite || used.signum() <= 0 || available.signum() <= 0) return 0;
        if (used.compareTo(available) >= 0) return 100;
        return new BigDecimal(used.multiply(BigInteger.valueOf(100)))
            .divide(new BigDecimal(available), MathContext.DECIMAL64)
            .floatValue();
    }
}
