package com.silvia.apeiron.math;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;

import org.junit.Test;

public class RecipeDisplayNumbersTest {

    @Test
    public void usesExactCountsForRates() {
        assertEquals("20", RecipeDisplayNumbers.rate(BigInteger.valueOf(40), 40, 20));
        assertEquals("0.5", RecipeDisplayNumbers.rate(BigInteger.valueOf(20), 40, 1));
        assertEquals("1.0E600", RecipeDisplayNumbers.rate(BigInteger.TEN.pow(600), 20, 20));
        assertEquals("0", RecipeDisplayNumbers.rate(BigInteger.ONE, 0, 20));
    }

    @Test
    public void displaysTheActualWirelessDebitIncludingEfficiency() {
        assertEquals(BigInteger.valueOf(12), RecipeDisplayNumbers.effectiveEUt(BigInteger.valueOf(10), 9000));
        assertEquals(BigInteger.TEN.pow(600), RecipeDisplayNumbers.effectiveEUt(BigInteger.TEN.pow(600), 10000));
        assertEquals(BigInteger.valueOf(100), RecipeDisplayNumbers.effectiveEUt(BigInteger.TEN, 0));
    }
}
