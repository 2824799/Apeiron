package com.silvia.apeiron.math;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;

import org.junit.Test;

public class CraftingPlanAmountsTest {

    @Test
    public void dividesWholeInventoryBeyondLong() {
        assertEquals(
            0.009f,
            CraftingPlanAmounts.usedPercent(new BigInteger("9000000000000000000"), BigInteger.TEN.pow(23), false),
            0.000001f);
        assertEquals(
            25f,
            CraftingPlanAmounts.usedPercent(
                BigInteger.TEN.pow(600),
                BigInteger.TEN.pow(600)
                    .multiply(BigInteger.valueOf(4)),
                false),
            0f);
    }

    @Test
    public void handlesOrdinaryAndInfiniteSupply() {
        assertEquals(50f, CraftingPlanAmounts.usedPercent(BigInteger.valueOf(32), BigInteger.valueOf(64), false), 0f);
        assertEquals(0f, CraftingPlanAmounts.usedPercent(BigInteger.TEN.pow(600), BigInteger.ONE, true), 0f);
        assertEquals(0f, CraftingPlanAmounts.usedPercent(BigInteger.ONE, BigInteger.ZERO, false), 0f);
        assertEquals(100f, CraftingPlanAmounts.usedPercent(BigInteger.valueOf(2), BigInteger.ONE, false), 0f);
    }
}
