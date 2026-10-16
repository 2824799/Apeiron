package com.silvia.apeiron.math;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;

import org.junit.Test;

public class BigNumberFormatterTest {

    @Test
    public void fitsPatternMultipliersWithUnitsAndScientificFallback() {
        assertEquals("1", BigNumberFormatter.formatSlotMultiplier(BigInteger.ONE));
        assertEquals("999", BigNumberFormatter.formatSlotMultiplier(BigInteger.valueOf(999)));
        assertEquals("1\nk", BigNumberFormatter.formatSlotMultiplier(BigInteger.valueOf(1000)));
        assertEquals("1.23\nk", BigNumberFormatter.formatSlotMultiplier(BigInteger.valueOf(1234)));
        assertEquals("12.3\nk", BigNumberFormatter.formatSlotMultiplier(BigInteger.valueOf(12345)));
        assertEquals("123\nk", BigNumberFormatter.formatSlotMultiplier(BigInteger.valueOf(123456)));
        assertEquals("1\nM", BigNumberFormatter.formatSlotMultiplier(BigInteger.valueOf(999999)));
        assertEquals("25\nM", BigNumberFormatter.formatSlotMultiplier(BigInteger.valueOf(25000000)));
        assertEquals("1\nG", BigNumberFormatter.formatSlotMultiplier(BigInteger.TEN.pow(9)));
        assertEquals("1\nT", BigNumberFormatter.formatSlotMultiplier(BigInteger.TEN.pow(12)));
        assertEquals(
            "1\nE15",
            BigNumberFormatter.formatSlotMultiplier(
                BigInteger.TEN.pow(15)
                    .subtract(BigInteger.ONE)));
        assertEquals(
            "1.23\nE600",
            BigNumberFormatter.formatSlotMultiplier(
                BigInteger.valueOf(123)
                    .multiply(BigInteger.TEN.pow(598))));
        assertEquals(
            "1\nE601",
            BigNumberFormatter.formatSlotMultiplier(
                BigInteger.TEN.pow(601)
                    .subtract(BigInteger.ONE)));
        assertEquals("1\nE32766", BigNumberFormatter.formatSlotMultiplier(BigInteger.TEN.pow(32766)));
    }

    @Test
    public void formatsCompactCountsWithOneDecimalPlace() {
        assertEquals(
            "9.2E18",
            BigNumberFormatter.formatCompact(
                BigInteger.valueOf(Long.MAX_VALUE)
                    .add(BigInteger.ONE)));
        assertEquals("1.0E19", BigNumberFormatter.formatCompact(BigInteger.TEN.pow(19)));
        assertEquals("9.9E19", BigNumberFormatter.formatCompact(new BigInteger("99400000000000000000")));
        assertEquals("1.0E20", BigNumberFormatter.formatCompact(new BigInteger("99600000000000000000")));
        assertEquals("-1.0E20", BigNumberFormatter.formatCompact(new BigInteger("-99600000000000000000")));
        assertEquals(
            Long.toString(Long.MAX_VALUE),
            BigNumberFormatter.formatCompact(BigInteger.valueOf(Long.MAX_VALUE)));
    }

    @Test
    public void formatsTooltipsWithoutLosingDigits() {
        BigInteger exact = BigInteger.ONE.shiftLeft(128)
            .add(BigInteger.valueOf(37L));
        assertEquals("340,282,366,920,938,463,463,374,607,431,768,211,493", BigNumberFormatter.formatExact(exact));
    }
}
