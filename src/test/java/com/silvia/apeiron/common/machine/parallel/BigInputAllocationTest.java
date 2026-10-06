package com.silvia.apeiron.common.machine.parallel;

import static org.junit.Assert.*;

import java.math.BigInteger;

import org.junit.Test;

import com.silvia.apeiron.api.machine.parallel.ParallelLimit;

public class BigInputAllocationTest {

    @Test
    public void renewableCircuitSupplyDoesNotLimitFiniteMaterials() {
        BigInputAllocation allocation = new BigInputAllocation(
            new BigInteger[] { BigInteger.ZERO, BigInteger.TEN.pow(60) },
            new BigInteger[] { BigInteger.ONE, BigInteger.valueOf(2) },
            new boolean[][] { { true, false }, { false, true } },
            new boolean[] { true, false });
        assertEquals(
            BigInteger.TEN.pow(60)
                .divide(BigInteger.valueOf(2)),
            allocation.maximum(ParallelLimit.unlimited()));
        assertNotNull(allocation.allocate(BigInteger.TEN.pow(40)));
    }

    @Test
    public void whollyRenewableInputsUseTheActualMachineLimit() {
        BigInteger limit = BigInteger.TEN.pow(90);
        BigInputAllocation allocation = new BigInputAllocation(
            new BigInteger[] { BigInteger.ZERO },
            new BigInteger[] { BigInteger.valueOf(3), BigInteger.valueOf(2) },
            new boolean[][] { { true }, { true } },
            new boolean[] { true });
        assertEquals(limit, allocation.maximum(ParallelLimit.bounded(limit)));
        assertArrayEquals(new BigInteger[] { limit.multiply(BigInteger.valueOf(5)) }, allocation.allocate(limit));
    }

    @Test
    public void sharedIngredientsAreNotSpentTwice() {
        BigInputAllocation allocation = new BigInputAllocation(
            new BigInteger[] { BigInteger.TEN.pow(60) },
            new BigInteger[] { BigInteger.ONE, BigInteger.ONE },
            new boolean[][] { { true }, { true } });
        assertEquals(
            BigInteger.TEN.pow(60)
                .divide(BigInteger.valueOf(2)),
            allocation.maximum(ParallelLimit.unlimited()));
        assertNull(allocation.allocate(BigInteger.TEN.pow(60)));
    }

    @Test
    public void alternativeMatchingCanReallocateBroadInputs() {
        BigInputAllocation allocation = new BigInputAllocation(
            new BigInteger[] { BigInteger.ONE, BigInteger.ONE },
            new BigInteger[] { BigInteger.ONE, BigInteger.ONE },
            new boolean[][] { { true, true }, { true, false } });
        assertEquals(BigInteger.ONE, allocation.maximum(ParallelLimit.unlimited()));
        assertArrayEquals(new BigInteger[] { BigInteger.ONE, BigInteger.ONE }, allocation.allocate(BigInteger.ONE));
    }

    @Test
    public void catalystOnlyRecipesUseTheEnergyOrManualBoundWithoutConsumingTheCircuit() {
        BigInteger bound = BigInteger.TEN.pow(70);
        BigInputAllocation allocation = new BigInputAllocation(
            new BigInteger[] { BigInteger.ONE },
            new BigInteger[0],
            new boolean[0][]);
        assertEquals(bound, allocation.maximum(ParallelLimit.bounded(bound)));
        assertArrayEquals(new BigInteger[] { BigInteger.ZERO }, allocation.allocate(bound));
        assertEquals(BigInteger.ONE, allocation.maximum(ParallelLimit.unlimited()));
    }

    @Test
    public void manualCapAndEmptyCostsHaveFiniteResults() {
        BigInputAllocation allocation = new BigInputAllocation(
            new BigInteger[] { BigInteger.TEN.pow(600) },
            new BigInteger[] { BigInteger.ONE },
            new boolean[][] { { true } });
        assertEquals(BigInteger.TEN.pow(100), allocation.maximum(ParallelLimit.bounded(BigInteger.TEN.pow(100))));
        assertEquals(
            BigInteger.ONE,
            new BigInputAllocation(new BigInteger[0], new BigInteger[0], new boolean[0][])
                .maximum(ParallelLimit.unlimited()));
    }
}
