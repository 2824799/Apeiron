package com.silvia.apeiron.common.machine.parallel;

import java.math.BigInteger;
import java.util.Objects;

import com.silvia.apeiron.api.machine.parallel.ParallelLimit;
import com.silvia.apeiron.math.AdaptiveInteger;

/** An immutable arithmetic plan. Simulation and resource commits belong to the controller adapter. */
public final class BigParallelPlan {

    private final BigInteger parallels;
    private final BigInteger euPerTick;
    private final BigInteger totalEU;
    private final int durationTicks;

    private BigParallelPlan(final BigInteger parallels, final BigInteger recipeEUt, final int durationTicks) {
        this.parallels = parallels;
        final AdaptiveInteger energy = new AdaptiveInteger(recipeEUt);
        energy.multiply(parallels);
        euPerTick = energy.toBigInteger();
        energy.multiply(durationTicks);
        totalEU = energy.toBigInteger();
        this.durationTicks = durationTicks;
    }

    /**
     * Limits a finite input snapshot by the machine, output space and one tick of direct wireless EU.
     * The balance is advisory, not reserved; the running controller must debit EU successfully before advancing.
     */
    public static BigParallelPlan calculate(final ParallelLimit machineLimit, final BigInteger inputParallelBound,
        final ParallelLimit outputLimit, final BigInteger availableEU, final BigInteger recipeEUt,
        final int durationTicks) {
        Objects.requireNonNull(machineLimit, "machineLimit");
        Objects.requireNonNull(outputLimit, "outputLimit");
        requireNonnegative(inputParallelBound);
        requireNonnegative(availableEU);
        requireNonnegative(recipeEUt);
        if (durationTicks < 1) throw new IllegalArgumentException("Recipe duration must be positive");
        BigInteger parallels = outputLimit.applyTo(machineLimit.applyTo(inputParallelBound));
        if (recipeEUt.signum() > 0) {
            parallels = parallels.min(availableEU.divide(recipeEUt));
        }
        return new BigParallelPlan(parallels, recipeEUt, durationTicks);
    }

    /** One consumed ingredient after ore-dictionary/NBT normalization; catalysts are checked separately. */
    public static BigInteger inputParallelBound(final BigInteger available, final BigInteger amountPerRecipe) {
        requireNonnegative(available);
        requireNonnegative(amountPerRecipe);
        if (amountPerRecipe.signum() == 0) throw new IllegalArgumentException("Consumed ingredient must be positive");
        return available.divide(amountPerRecipe);
    }

    public BigInteger getParallelsBig() {
        return parallels;
    }

    public int getParallels() {
        return parallels.min(BigInteger.valueOf(Integer.MAX_VALUE))
            .intValue();
    }

    public BigInteger getEUtBig() {
        return euPerTick;
    }

    public long getEUt() {
        return new AdaptiveInteger(euPerTick).longValueSaturated();
    }

    public BigInteger getTotalEUBig() {
        return totalEU;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public BigInteger scaleAmountBig(final BigInteger amountPerRecipe) {
        requireNonnegative(amountPerRecipe);
        final AdaptiveInteger quantity = new AdaptiveInteger(amountPerRecipe);
        quantity.multiply(parallels);
        return quantity.toBigInteger();
    }

    private static void requireNonnegative(final BigInteger value) {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) throw new IllegalArgumentException("Negative parallel quantity or energy");
    }
}
