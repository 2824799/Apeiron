package com.silvia.apeiron.math;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Overclock decisions use full power products; final EU is rounded once without an int/long compatibility view. */
public final class ExactOverclock {

    private ExactOverclock() {}

    public static BigDecimal power(long recipeEUt, BigInteger lanes, double discount, double heatDiscount) {
        return BigDecimal.valueOf(recipeEUt)
            .multiply(new BigDecimal(lanes))
            .multiply(decimal(discount))
            .multiply(decimal(heatDiscount));
    }

    public static BigInteger ceil(BigDecimal value) {
        return value.setScale(0, RoundingMode.CEILING)
            .toBigIntegerExact();
    }

    public static BigInteger multiplyCeil(BigInteger count, double multiplier) {
        return ceil(new BigDecimal(count).multiply(decimal(multiplier)));
    }

    public static BigDecimal decimal(double value) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid power modifier");
        return BigDecimal.valueOf(value);
    }

    public static int tiers(BigDecimal available, BigDecimal required, double increase) {
        if (increase <= 1 || !Double.isFinite(increase)) throw new IllegalArgumentException("Invalid OC ratio");
        if (available.compareTo(required) < 0) return -1;
        BigDecimal threshold = required.max(BigDecimal.valueOf(32));
        BigDecimal factor = BigDecimal.valueOf(increase);
        int result = 0;
        while (threshold.multiply(factor)
            .compareTo(available) <= 0) {
            threshold = threshold.multiply(factor);
            result++;
            if (result > 4096) throw new IllegalArgumentException("Unbounded overclock configuration");
        }
        return result;
    }

    public static BigInteger energy(BigDecimal basePower, double increase, int overclocks) {
        return ceil(basePower.multiply(decimal(increase).pow(Math.max(0, overclocks))));
    }
}
