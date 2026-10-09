package com.silvia.apeiron.common.machine.parallel;

import static org.junit.Assert.*;

import java.math.BigInteger;
import java.util.Arrays;

import org.junit.Test;

import com.silvia.apeiron.api.machine.parallel.ParallelLimit;

public class ItemProcessingPlanTest {

    @Test
    public void largeMixedInventoryChecksTheAffordableBatchOnlyOnce() {
        BigInteger[] available = new BigInteger[1024], costs = new BigInteger[1024];
        Arrays.fill(available, BigInteger.TEN.pow(60));
        Arrays.fill(costs, BigInteger.ONE);
        int[] checks = { 0 };
        ItemProcessingPlan plan = ItemProcessingPlan.calculate(
            available,
            new boolean[available.length],
            costs,
            ParallelLimit.unlimited(),
            BigInteger.TEN.pow(70),
            debit -> {
                checks[0]++;
                assertArrayEquals(available, debit);
                return true;
            });
        assertEquals(1, checks[0]);
        assertArrayEquals(available, plan.getDebits());
        assertEquals(
            BigInteger.TEN.pow(60)
                .multiply(BigInteger.valueOf(1024)),
            plan.getTotalEUBig());
    }

    @Test
    public void capacityFallbackReusesEnergyAndParallelRoomFreedByEarlierInputs() {
        ItemProcessingPlan plan = ItemProcessingPlan.calculate(
            values(10, 10),
            new boolean[2],
            values(2, 1),
            ParallelLimit.bounded(10),
            BigInteger.valueOf(20),
            debit -> debit[0].compareTo(BigInteger.valueOf(3)) <= 0);
        assertArrayEquals(values(3, 7), plan.getDebits());
        assertEquals(BigInteger.valueOf(13), plan.getTotalEUBig());
    }

    @Test
    public void mixedRecipesShareEnergyAndParallelBudget() {
        ItemProcessingPlan plan = ItemProcessingPlan.calculate(
            values(7, 9, 100),
            new boolean[3],
            new BigInteger[] { BigInteger.valueOf(3), BigInteger.valueOf(5), null },
            ParallelLimit.bounded(10),
            BigInteger.valueOf(31),
            debit -> true);
        assertArrayEquals(values(7, 2, 0), plan.getDebits());
        assertEquals(BigInteger.valueOf(9), plan.getParallelsBig());
        assertEquals(BigInteger.valueOf(31), plan.getTotalEUBig());
    }

    @Test
    public void outputCapacityChecksCombinedOutputsAndKeepsAffordableRemainder() {
        ItemProcessingPlan plan = ItemProcessingPlan.calculate(
            values(8, 9),
            new boolean[2],
            values(2, 1),
            ParallelLimit.unlimited(),
            BigInteger.valueOf(100),
            debit -> debit[0].multiply(BigInteger.valueOf(3))
                .add(debit[1])
                .compareTo(BigInteger.valueOf(10)) <= 0);
        assertArrayEquals(values(3, 1), plan.getDebits());
        assertEquals(BigInteger.valueOf(7), plan.getTotalEUBig());
    }

    @Test
    public void quantitiesAndFactorsAboveLongStayExact() {
        BigInteger huge = BigInteger.TEN.pow(60)
            .add(BigInteger.valueOf(17));
        ItemProcessingPlan plan = ItemProcessingPlan.calculate(
            new BigInteger[] { huge, huge },
            new boolean[2],
            new BigInteger[] { huge, BigInteger.ZERO },
            ParallelLimit.unlimited(),
            huge.multiply(huge),
            debit -> true);
        assertEquals(huge.multiply(huge), plan.getTotalEUBig());
        assertEquals(huge.multiply(BigInteger.valueOf(2)), plan.getParallelsBig());
        assertArrayEquals(new BigInteger[] { huge, huge }, plan.getDebits());
    }

    @Test
    public void renewableInputsUseFinitePowerOrManualLimit() {
        ItemProcessingPlan powered = ItemProcessingPlan.calculate(
            values(0),
            new boolean[] { true },
            values(7),
            ParallelLimit.unlimited(),
            BigInteger.valueOf(23),
            debit -> true);
        assertArrayEquals(values(3), powered.getDebits());
        ItemProcessingPlan free = ItemProcessingPlan.calculate(
            values(0),
            new boolean[] { true },
            values(0),
            ParallelLimit.bounded(12),
            BigInteger.ZERO,
            debit -> true);
        assertArrayEquals(values(12), free.getDebits());
        assertThrows(
            IllegalArgumentException.class,
            () -> ItemProcessingPlan.calculate(
                values(0),
                new boolean[] { true },
                values(0),
                ParallelLimit.unlimited(),
                BigInteger.ZERO,
                debit -> true));
    }

    @Test
    public void freeReturnsWorkWithoutPowerButStillRespectManualCap() {
        ItemProcessingPlan plan = ItemProcessingPlan.calculate(
            values(8, 9),
            new boolean[2],
            values(4, 0),
            ParallelLimit.bounded(6),
            BigInteger.ZERO,
            debit -> true);
        assertArrayEquals(values(0, 6), plan.getDebits());
        assertEquals(BigInteger.ZERO, plan.getTotalEUBig());
    }

    @Test
    public void capacityOracleCannotMutateTheDebitPlan() {
        ItemProcessingPlan plan = ItemProcessingPlan
            .calculate(values(3), new boolean[1], values(2), ParallelLimit.unlimited(), BigInteger.TEN, debit -> {
                debit[0] = BigInteger.ZERO;
                return true;
            });
        BigInteger[] debit = plan.getDebits();
        debit[0] = BigInteger.ZERO;
        assertArrayEquals(values(3), plan.getDebits());
        assertEquals(BigInteger.valueOf(6), plan.getTotalEUBig());
    }

    private static BigInteger[] values(long... values) {
        return Arrays.stream(values)
            .mapToObj(BigInteger::valueOf)
            .toArray(BigInteger[]::new);
    }
}
