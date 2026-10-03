package com.silvia.apeiron.math;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;

import org.junit.Test;

public class RecipeEnergyBudgetTest {

    @Test
    public void plansWholeRecipeRatherThanOneTick() {
        assertEquals(
            BigInteger.valueOf(5),
            RecipeEnergyBudget.affordable(BigInteger.valueOf(800), BigInteger.valueOf(160)));
        assertEquals(
            BigInteger.valueOf(5),
            RecipeEnergyBudget.affordableNormal(BigInteger.valueOf(800), BigInteger.valueOf(8), 20, 10000));
        assertEquals(
            BigInteger.valueOf(4),
            RecipeEnergyBudget.affordableNormal(BigInteger.valueOf(800), BigInteger.valueOf(8), 20, 9000));
    }

    @Test
    public void preservesExactTotalForAnyCompletionTime() {
        BigInteger total = BigInteger.TEN.pow(80)
            .add(BigInteger.valueOf(37));
        for (int duration : new int[] { 1, 7, 128, 1000 }) {
            BigInteger sum = BigInteger.ZERO;
            for (int tick = 0; tick < duration; tick++)
                sum = sum.add(RecipeEnergyBudget.tickCost(total, duration, tick));
            assertEquals(total, sum);
        }
    }

    @Test
    public void zeroCostTicksDoNotCreateOrLoseEnergy() {
        BigInteger sum = BigInteger.ZERO;
        for (int tick = 0; tick < 128; tick++)
            sum = sum.add(RecipeEnergyBudget.tickCost(BigInteger.valueOf(3), 128, tick));
        assertEquals(BigInteger.valueOf(3), sum);
        assertEquals(BigInteger.ONE, RecipeEnergyBudget.tickCost(BigInteger.valueOf(3), 128, 0));
        assertEquals(BigInteger.ZERO, RecipeEnergyBudget.tickCost(BigInteger.valueOf(3), 128, 127));
    }

    @Test
    public void maintenanceBudgetMatchesRoundedBatchDebit() {
        BigInteger affordable = RecipeEnergyBudget
            .affordableNormal(BigInteger.valueOf(1000), BigInteger.valueOf(1), 20, 9000);
        assertEquals(BigInteger.valueOf(45), affordable);
        assertEquals(
            BigInteger.valueOf(1000),
            RecipeDisplayNumbers.effectiveEUt(affordable, 9000)
                .multiply(BigInteger.valueOf(20)));
    }
}
