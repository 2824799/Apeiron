package com.silvia.apeiron.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.math.BigInteger;

import org.junit.Test;

public class BigOutputScalingTest {

    @Test
    public void scalesBeyondLongWithoutAnIntermediateFloatingPointCount() {
        final BigInteger base = BigInteger.valueOf(Long.MAX_VALUE);
        assertEquals(base.multiply(BigInteger.valueOf(3)), BigOutputScaling.scale(base, 0.75D, 4));
        assertEquals(
            BigInteger.TEN.pow(60)
                .multiply(BigInteger.valueOf(18)),
            BigOutputScaling.scale(BigInteger.TEN.pow(60), 0.9D, 20));
        assertEquals(BigInteger.valueOf(31), BigOutputScaling.scale(BigInteger.valueOf(7), 0.9D, 5));
        assertEquals(base, BigOutputScaling.scale(base, 1.0D, 1));
    }

    @Test
    public void rejectsInvalidYieldAndCounts() {
        assertThrows(IllegalArgumentException.class, () -> BigOutputScaling.scale(BigInteger.ONE, Double.NaN, 1));
        assertThrows(
            IllegalArgumentException.class,
            () -> BigOutputScaling.scale(BigInteger.ONE, Double.POSITIVE_INFINITY, 1));
        assertThrows(IllegalArgumentException.class, () -> BigOutputScaling.scale(BigInteger.ONE, -0.1D, 1));
        assertThrows(IllegalArgumentException.class, () -> BigOutputScaling.scale(BigInteger.ONE.negate(), 1.0D, 1));
        assertThrows(IllegalArgumentException.class, () -> BigOutputScaling.scale(BigInteger.ONE, 1.0D, -1));
    }
}
