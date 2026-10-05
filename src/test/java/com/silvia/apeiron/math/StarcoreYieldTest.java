package com.silvia.apeiron.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;

import org.junit.Test;

public class StarcoreYieldTest {

    @Test
    public void keepsNativeSmallMultipliers() {
        assertEquals(BigInteger.valueOf(131072), StarcoreYield.amount(131072, 0));
        assertEquals(BigInteger.valueOf(262144), StarcoreYield.amount(131072, 1));
        assertEquals(BigInteger.valueOf(786432), StarcoreYield.amount(131072, 2));
        assertEquals(BigInteger.valueOf(134217728), StarcoreYield.amount(131072, 64));
    }

    @Test
    public void doesNotOverflowConfiguredIntMaximum() {
        assertEquals(
            BigInteger.valueOf(Integer.MAX_VALUE)
                .multiply(BigInteger.valueOf(1024)),
            StarcoreYield.amount(Integer.MAX_VALUE, 64));
        assertTrue(
            StarcoreYield.amount(Integer.MAX_VALUE, Integer.MAX_VALUE)
                .compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0);
    }
}
