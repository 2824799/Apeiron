package com.silvia.apeiron.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.Test;

public class ExactOverclockTest {

    @Test
    public void recipePowerDoesNotWrapBeforeDiscount() {
        BigInteger lanes = BigInteger.valueOf(Integer.MAX_VALUE);
        BigDecimal power = ExactOverclock.power(Long.MAX_VALUE, lanes, 0.45, 1);
        assertEquals(
            0,
            new BigDecimal(Long.MAX_VALUE).multiply(new BigDecimal(lanes))
                .multiply(new BigDecimal("0.45"))
                .compareTo(power));
        assertTrue(
            ExactOverclock.ceil(power)
                .compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0);
    }

    @Test
    public void tierComparisonKeepsBothLongPowerFactors() {
        BigInteger machine = BigInteger.valueOf(2_147_483_647L)
            .multiply(BigInteger.valueOf(8_589_934_590L));
        assertEquals(29, ExactOverclock.tiers(new BigDecimal(machine), BigDecimal.valueOf(32), 4));
        assertEquals(-1, ExactOverclock.tiers(BigDecimal.valueOf(31), BigDecimal.valueOf(32), 4));
        assertEquals(0, ExactOverclock.tiers(BigDecimal.valueOf(32), BigDecimal.valueOf(32), 4));
    }

    @Test
    public void finalEnergyIsRoundedOnceBeyondLong() {
        BigDecimal base = new BigDecimal("4150517416584649113.15");
        assertEquals(new BigInteger("16602069666338596453"), ExactOverclock.energy(base, 4, 1));
        assertEquals(
            new BigInteger("33204139332677192906"),
            ExactOverclock.energy(base.multiply(BigDecimal.valueOf(2)), 4, 1));
    }

    @Test
    public void subTickParallelMultiplierIsNotAnIntCompatibilityView() {
        assertEquals(
            new BigInteger("34359738352000000000"),
            ExactOverclock.multiplyCeil(BigInteger.valueOf(Integer.MAX_VALUE), 16_000_000_000.0));
    }
}
