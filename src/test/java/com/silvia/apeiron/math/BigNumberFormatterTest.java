package com.silvia.apeiron.math;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;

import org.junit.Test;

public class BigNumberFormatterTest {

    @Test
    public void formatsCompactCountsWithThreeSignificantDigits() {
        assertEquals(
            "9.22E18",
            BigNumberFormatter.formatCompact(
                BigInteger.valueOf(Long.MAX_VALUE)
                    .add(BigInteger.ONE)));
        assertEquals("1.00E19", BigNumberFormatter.formatCompact(BigInteger.TEN.pow(19)));
    }

    @Test
    public void formatsTooltipsWithoutLosingDigits() {
        BigInteger exact = BigInteger.ONE.shiftLeft(128)
            .add(BigInteger.valueOf(37L));
        assertEquals("340,282,366,920,938,463,463,374,607,431,768,211,493", BigNumberFormatter.formatExact(exact));
    }
}
