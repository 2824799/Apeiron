package com.silvia.apeiron.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class ChancedParallelCountsTest {

    @Test
    public void fractionalSingleParallelCanSucceedOrFail() {
        assertEquals(1L, ChancedParallelCounts.calculate(1L, 0.5D, () -> 0.25D));
        assertEquals(0L, ChancedParallelCounts.calculate(1L, 0.5D, () -> 0.75D));
        assertEquals(0L, ChancedParallelCounts.calculate(1L, 0.5D, () -> 0.5D));
        assertEquals(1L, ChancedParallelCounts.calculate(1L, 1.0E-18D, () -> 0.0D));
        assertEquals(0L, ChancedParallelCounts.calculate(1L, 1.0E-18D, () -> 1.0E-18D));
    }

    @Test
    public void batchesRetainTheirIntegralYieldAndRollOnlyTheRemainder() {
        assertEquals(4L, ChancedParallelCounts.calculate(7L, 0.5D, () -> 0.1D));
        assertEquals(3L, ChancedParallelCounts.calculate(7L, 0.5D, () -> 0.9D));
        assertEquals(
            23L,
            ChancedParallelCounts.calculate(
                100L,
                0.23D,
                () -> { throw new AssertionError("Integral yield should not draw randomness"); }));
    }

    @Test
    public void longParallelCountsKeepTheLowBitsAndFraction() {
        assertEquals(4_611_686_018_427_387_904L, ChancedParallelCounts.calculate(Long.MAX_VALUE, 0.5D, () -> 0.1D));
        assertEquals(4_611_686_018_427_387_903L, ChancedParallelCounts.calculate(Long.MAX_VALUE, 0.5D, () -> 0.9D));
        assertEquals(922_337_203_685_477_581L, ChancedParallelCounts.calculate(Long.MAX_VALUE, 0.1D, () -> 0.1D));
        assertEquals(922_337_203_685_477_580L, ChancedParallelCounts.calculate(Long.MAX_VALUE, 0.1D, () -> 0.9D));
    }

    @Test
    public void zeroAndFullChanceAreExactWithoutRandomness() {
        assertEquals(0L, ChancedParallelCounts.calculate(0L, 0.5D, null));
        assertEquals(0L, ChancedParallelCounts.calculate(Long.MAX_VALUE, 0.0D, null));
        assertEquals(Long.MAX_VALUE, ChancedParallelCounts.calculate(Long.MAX_VALUE, 1.0D, null));
    }

    @Test
    public void invalidCountsProbabilitiesAndRollsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ChancedParallelCounts.calculate(-1L, 0.5D, null));
        assertThrows(IllegalArgumentException.class, () -> ChancedParallelCounts.calculate(1L, Double.NaN, null));
        assertThrows(IllegalArgumentException.class, () -> ChancedParallelCounts.calculate(1L, -0.1D, null));
        assertThrows(IllegalArgumentException.class, () -> ChancedParallelCounts.calculate(1L, 1.1D, null));
        assertThrows(IllegalArgumentException.class, () -> ChancedParallelCounts.calculate(1L, 0.5D, () -> 1.0D));
        assertThrows(IllegalArgumentException.class, () -> ChancedParallelCounts.calculate(1L, 0.5D, () -> Double.NaN));
    }
}
