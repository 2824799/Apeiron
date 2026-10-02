package com.silvia.apeiron.common.machine.parallel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.math.BigInteger;

import org.junit.Test;

import com.silvia.apeiron.api.machine.parallel.ParallelLimit;

public class BigParallelPlanTest {

    @Test
    public void preservesBeyondLongParallelsInputsAndEnergyWithoutChunking() {
        final BigInteger parallel = BigInteger.TEN.pow(60)
            .add(BigInteger.valueOf(17));
        final BigInteger amount = parallel.multiply(BigInteger.valueOf(3))
            .add(BigInteger.valueOf(2));
        final BigInteger inputBound = BigParallelPlan.inputParallelBound(amount, BigInteger.valueOf(3));
        final BigParallelPlan plan = BigParallelPlan.calculate(
            ParallelLimit.unlimited(),
            inputBound,
            ParallelLimit.unlimited(),
            parallel.multiply(BigInteger.valueOf(32)),
            BigInteger.valueOf(32),
            200);
        assertEquals(parallel, plan.getParallelsBig());
        assertEquals(parallel.multiply(BigInteger.valueOf(3)), plan.scaleAmountBig(BigInteger.valueOf(3)));
        assertEquals(parallel.multiply(BigInteger.valueOf(32)), plan.getEUtBig());
        assertEquals(parallel.multiply(BigInteger.valueOf(6400)), plan.getTotalEUBig());
        assertEquals(Integer.MAX_VALUE, plan.getParallels());
        assertEquals(Long.MAX_VALUE, plan.getEUt());
    }

    @Test
    public void respectsFiniteMachineOutputAndAccountBounds() {
        final BigInteger inputs = BigInteger.valueOf(1000);
        assertEquals(
            BigInteger.valueOf(7),
            plan(ParallelLimit.bounded(7), inputs, ParallelLimit.bounded(11), 320).getParallelsBig());
        assertEquals(
            BigInteger.valueOf(9),
            plan(ParallelLimit.unlimited(), inputs, ParallelLimit.bounded(9), 320).getParallelsBig());
        assertEquals(
            BigInteger.valueOf(10),
            plan(ParallelLimit.unlimited(), inputs, ParallelLimit.unlimited(), 335).getParallelsBig());
        assertEquals(
            BigInteger.ZERO,
            plan(ParallelLimit.unlimited(), inputs, ParallelLimit.unlimited(), 31).getParallelsBig());
    }

    @Test
    public void zeroCostStillHasAFiniteInputBoundAndInvalidQuantitiesAreRejected() {
        final BigInteger finiteInput = BigInteger.TEN.pow(100);
        final BigParallelPlan plan = BigParallelPlan.calculate(
            ParallelLimit.unlimited(),
            finiteInput,
            ParallelLimit.unlimited(),
            BigInteger.ZERO,
            BigInteger.ZERO,
            1);
        assertEquals(finiteInput, plan.getParallelsBig());
        assertEquals(BigInteger.ZERO, plan.getTotalEUBig());
        assertThrows(IllegalArgumentException.class, () -> ParallelLimit.bounded(-1));
        assertThrows(
            IllegalArgumentException.class,
            () -> BigParallelPlan.inputParallelBound(BigInteger.ONE, BigInteger.ZERO));
        assertThrows(
            IllegalArgumentException.class,
            () -> BigParallelPlan.calculate(
                ParallelLimit.unlimited(),
                BigInteger.ONE,
                ParallelLimit.unlimited(),
                BigInteger.ZERO,
                BigInteger.valueOf(-1),
                1));
        assertThrows(
            IllegalArgumentException.class,
            () -> BigParallelPlan.calculate(
                ParallelLimit.unlimited(),
                BigInteger.ONE,
                ParallelLimit.unlimited(),
                BigInteger.ZERO,
                BigInteger.ZERO,
                0));
    }

    private static BigParallelPlan plan(final ParallelLimit machine, final BigInteger inputs,
        final ParallelLimit outputs, final long energy) {
        return BigParallelPlan
            .calculate(machine, inputs, outputs, BigInteger.valueOf(energy), BigInteger.valueOf(32), 100);
    }
}
