package com.silvia.apeiron.math;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;

import org.junit.Test;

public class ExactAmountExpressionTest {

    @Test
    public void retainsIntegersAndScientificAmountsBeyondLong() {
        assertEquals(BigInteger.ONE, ExactAmountExpression.parse("1"));
        assertEquals(BigInteger.TEN.pow(19), ExactAmountExpression.parse("10^19"));
        assertEquals(BigInteger.TEN.pow(19), ExactAmountExpression.parse("1e19"));
        assertEquals(
            BigInteger.TEN.pow(19)
                .add(BigInteger.ONE),
            ExactAmountExpression.parse("1e19+1"));
        assertEquals(new BigInteger("9007199254740993"), ExactAmountExpression.parse("9,007,199,254,740,993"));
    }

    @Test
    public void roundsOnceAfterExactArithmetic() {
        assertEquals(BigInteger.ONE, ExactAmountExpression.parse("(1/3)*3"));
        assertEquals(BigInteger.valueOf(2), ExactAmountExpression.parse("1.5"));
        assertEquals(
            BigInteger.TEN.pow(600)
                .add(BigInteger.valueOf(17)),
            ExactAmountExpression.parse("10^600+17"));
        assertEquals(BigInteger.valueOf(512), ExactAmountExpression.parse("2^3^2"));
    }
}
