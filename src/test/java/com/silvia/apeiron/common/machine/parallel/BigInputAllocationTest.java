package com.silvia.apeiron.common.machine.parallel;

import static org.junit.Assert.*;

import java.math.BigInteger;

import org.junit.Test;

import com.silvia.apeiron.api.machine.parallel.ParallelLimit;

public class BigInputAllocationTest {

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
