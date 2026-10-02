package com.silvia.apeiron.math;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;

import org.junit.Test;

public class BigNumberFormatterTest {

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
