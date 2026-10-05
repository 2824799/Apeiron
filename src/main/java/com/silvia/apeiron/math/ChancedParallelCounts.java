package com.silvia.apeiron.math;

import java.math.BigDecimal;
import java.util.function.DoubleSupplier;

/** Keeps the exact integral yield and rolls the remaining fraction, even for a single parallel. */
public final class ChancedParallelCounts {

    private ChancedParallelCounts() {}

    public static long calculate(long parallels, double chance, DoubleSupplier random) {
        if (parallels < 0 || !Double.isFinite(chance) || chance < 0.0D || chance > 1.0D)
            throw new IllegalArgumentException("Invalid parallel count or success chance");
        if (parallels == 0 || chance == 0.0D) return 0L;
        if (chance == 1.0D) return parallels;
        BigDecimal expected = BigDecimal.valueOf(parallels)
            .multiply(BigDecimal.valueOf(chance));
        long whole = expected.toBigInteger()
            .longValueExact();
        BigDecimal fraction = expected.subtract(BigDecimal.valueOf(whole));
        if (fraction.signum() == 0) return whole;
        double roll = random.getAsDouble();
        if (!Double.isFinite(roll) || roll < 0.0D || roll >= 1.0D)
            throw new IllegalArgumentException("Invalid probability roll");
        return fraction.compareTo(BigDecimal.valueOf(roll)) > 0 ? Math.addExact(whole, 1L) : whole;
    }
}
