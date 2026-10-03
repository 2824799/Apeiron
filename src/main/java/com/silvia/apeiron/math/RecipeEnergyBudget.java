package com.silvia.apeiron.math;

import java.math.BigInteger;

/** Plans a whole batch and distributes an exact total without rounding away or adding EU. */
public final class RecipeEnergyBudget {

    private RecipeEnergyBudget() {}

    public static BigInteger affordable(BigInteger available, BigInteger euPerRecipe) {
        if (available.signum() < 0 || euPerRecipe.signum() <= 0)
            throw new IllegalArgumentException("Invalid recipe energy budget");
        return available.divide(euPerRecipe);
    }

    public static BigInteger affordableNormal(BigInteger available, BigInteger euPerTick, int duration,
        int efficiency) {
        if (available.signum() < 0 || euPerTick.signum() <= 0 || duration < 1)
            throw new IllegalArgumentException("Invalid normal energy budget");
        return available.multiply(BigInteger.valueOf(Math.max(1000, efficiency)))
            .divide(
                euPerTick.multiply(BigInteger.valueOf(10000L))
                    .multiply(BigInteger.valueOf(duration)));
    }

    public static BigInteger tickCost(BigInteger total, int duration, int paidTicks) {
        if (total.signum() < 0 || duration < 1 || paidTicks < 0 || paidTicks >= duration)
            throw new IllegalArgumentException("Invalid recipe energy schedule");
        BigInteger[] parts = total.divideAndRemainder(BigInteger.valueOf(duration));
        return parts[0].add(paidTicks < parts[1].intValue() ? BigInteger.ONE : BigInteger.ZERO);
    }
}
