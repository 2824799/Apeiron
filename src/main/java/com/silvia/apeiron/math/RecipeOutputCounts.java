package com.silvia.apeiron.math;

import java.math.BigInteger;
import java.util.Random;

/** Native GT output chances include guaranteed rolls above 100%, followed by a fractional roll. */
public final class RecipeOutputCounts {

    private static final int SCALE = 10000;

    private RecipeOutputCounts() {}

    public static BigInteger maximum(BigInteger parallels, int chance) {
        validate(parallels, chance);
        return parallels.multiply(BigInteger.valueOf(((long) chance + SCALE - 1) / SCALE));
    }

    public static BigInteger roll(BigInteger parallels, int chance, Random random) {
        validate(parallels, chance);
        return parallels.multiply(BigInteger.valueOf(chance / SCALE))
            .add(MiningOutputCounts.binomial(parallels, chance % SCALE, SCALE, random));
    }

    private static void validate(BigInteger parallels, int chance) {
        if (parallels.signum() < 0 || chance < 0) throw new IllegalArgumentException("Negative output count/chance");
    }
}
