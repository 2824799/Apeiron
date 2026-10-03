package com.silvia.apeiron.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.math.BigInteger;

import org.junit.Test;

public class ScientificIntegerTest {

    @Test
    public void exponentFormsPreserveEveryDigit() {
        assertEquals(BigInteger.TEN.pow(18), ScientificInteger.positive("1E18"));
        assertEquals(BigInteger.TEN.pow(18), ScientificInteger.positive("1e18"));
        assertEquals(
            new BigInteger("123456789012345678901234567890"),
            ScientificInteger.positive("1.23456789012345678901234567890e29"));
        assertEquals(BigInteger.valueOf(123), ScientificInteger.positive("1230e-1"));
        assertEquals(BigInteger.TEN.pow(10000), ScientificInteger.positive("1e10000"));
    }

    @Test
    public void fractionsInvalidValuesAndResourceExhaustionAreRejected() {
        for (String value : new String[] { "0", "-1", "NaN", "Infinity", "1.5", "1e-2", "1e2147483647", "1e-2147483647",
            "1E", "1;2", "1,000" }) {
            try {
                ScientificInteger.positive(value);
                fail("Accepted " + value);
            } catch (IllegalArgumentException | ArithmeticException expected) {}
        }
    }
}
