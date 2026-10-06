package com.silvia.apeiron.math;

import static org.junit.Assert.*;

import java.math.BigInteger;
import java.util.Random;

import org.junit.Test;

public class RecipeOutputCountsTest {

    @Test
    public void guaranteedAndZeroChancesDoNotDrawRandomNumbers() {
        Random noRoll = new Random() {

            @Override
            protected int next(int bits) {
                throw new AssertionError("Guaranteed output drew random numbers");
            }
        };
        BigInteger huge = BigInteger.TEN.pow(100);
        assertEquals(BigInteger.ZERO, RecipeOutputCounts.roll(huge, 0, noRoll));
        assertEquals(huge, RecipeOutputCounts.roll(huge, 10000, noRoll));
        assertEquals(huge.multiply(BigInteger.valueOf(2)), RecipeOutputCounts.roll(huge, 20000, noRoll));
    }

    @Test
    public void chancesOverOneHundredPercentKeepGuaranteedAndFractionalRolls() {
        BigInteger trials = BigInteger.valueOf(80);
        assertEquals(
            trials.add(RecipeOutputCounts.roll(trials, 1250, new Random(71))),
            RecipeOutputCounts.roll(trials, 11250, new Random(71)));
        assertEquals(trials.multiply(BigInteger.valueOf(2)), RecipeOutputCounts.maximum(trials, 11250));
        assertEquals(trials, RecipeOutputCounts.maximum(trials, 1250));
    }

    @Test
    public void hugeProbabilityOutputsRemainInBoundsAndRetainAllDigits() {
        BigInteger trials = BigInteger.TEN.pow(100)
            .add(BigInteger.valueOf(17));
        BigInteger rolled = RecipeOutputCounts.roll(trials, 11250, new Random(71));
        assertTrue(rolled.compareTo(trials) >= 0);
        assertTrue(rolled.compareTo(RecipeOutputCounts.maximum(trials, 11250)) <= 0);
        assertTrue(rolled.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0);
        assertEquals(
            trials.multiply(BigInteger.valueOf(214749)),
            RecipeOutputCounts.maximum(trials, Integer.MAX_VALUE));
    }
}
