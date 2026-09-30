package com.silvia.apeiron.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;

import org.junit.Test;

public class AdaptiveIntegerTest {

    @Test
    public void crossesBothLongBoundariesAndCollapsesAgain() {
        AdaptiveInteger positive = new AdaptiveInteger(Long.MAX_VALUE);
        positive.add(1L);
        assertTrue(positive.isBig());
        assertEquals(
            BigInteger.valueOf(Long.MAX_VALUE)
                .add(BigInteger.ONE),
            positive.toBigInteger());
        assertEquals(Long.MAX_VALUE, positive.longValueSaturated());
        positive.subtract(1L);
        assertFalse(positive.isBig());
        assertEquals(Long.MAX_VALUE, positive.longValueExact());

        AdaptiveInteger negative = new AdaptiveInteger(Long.MIN_VALUE);
        negative.subtract(1L);
        assertTrue(negative.isBig());
        assertEquals(
            BigInteger.valueOf(Long.MIN_VALUE)
                .subtract(BigInteger.ONE),
            negative.toBigInteger());
        assertEquals(Long.MIN_VALUE, negative.longValueSaturated());
        negative.add(1L);
        assertFalse(negative.isBig());
        assertEquals(Long.MIN_VALUE, negative.longValueExact());
    }

    @Test
    public void handlesLongMinValueAndLargeMultiplication() {
        AdaptiveInteger value = new AdaptiveInteger(-1L);
        value.subtract(Long.MIN_VALUE);
        assertEquals(Long.MAX_VALUE, value.longValueExact());

        value.multiply(BigInteger.ONE.shiftLeft(80));
        assertEquals(
            BigInteger.valueOf(Long.MAX_VALUE)
                .shiftLeft(80),
            value.toBigInteger());
        assertTrue(value.isBig());
        value.multiply(0L);
        assertEquals(0L, value.longValueExact());
    }

    @Test
    public void copyAndComparePreserveExactValues() {
        AdaptiveInteger value = new AdaptiveInteger(BigInteger.ONE.shiftLeft(128));
        AdaptiveInteger copy = value.copy();
        assertEquals(0, value.compareTo(copy));
        copy.add(BigInteger.ONE);
        assertEquals(-1, value.compareTo(copy));
        assertEquals(BigInteger.ONE.shiftLeft(128), value.toBigInteger());
    }
}
