package com.silvia.apeiron.math;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public final class RecipeDisplayNumbers {

    private RecipeDisplayNumbers() {}

    public static String rate(BigInteger amount, int duration, long ticksPerUnit) {
        return rate(amount, (long) duration, ticksPerUnit);
    }

    public static String rate(BigInteger amount, long duration, long ticksPerUnit) {
        if (duration <= 0) return "0";
        final BigDecimal value = new BigDecimal(amount.multiply(BigInteger.valueOf(ticksPerUnit)))
            .divide(BigDecimal.valueOf(duration), 2, RoundingMode.HALF_UP);
        return value.abs()
            .compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0 ? BigNumberFormatter.formatCompact(value.toBigInteger())
                : value.stripTrailingZeros()
                    .toPlainString();
    }

    public static BigInteger effectiveEUt(BigInteger recipeEUt, int efficiency) {
        final BigInteger divisor = BigInteger.valueOf(Math.max(1000, efficiency));
        return recipeEUt.multiply(BigInteger.valueOf(10000))
            .add(divisor)
            .subtract(BigInteger.ONE)
            .divide(divisor);
    }
}
