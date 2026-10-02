package com.silvia.apeiron.common.machine.me.output.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;

import org.junit.Test;

public class BigCacheCounterTest {

    private static final BigInteger HUGE = BigInteger.TEN.pow(60)
        .add(BigInteger.valueOf(17));

    @Test
    public void aggregatesAcrossLongOverflowAndReturnsToSmallValues() {
        final BigCacheCounter<String> cache = new BigCacheCounter<>();
        cache.insert("a", Long.MAX_VALUE);
        cache.insert("a", 1);
        cache.insertBig("b", HUGE);
        assertEquals(
            BigInteger.valueOf(Long.MAX_VALUE)
                .add(BigInteger.ONE),
            cache.getBig("a"));
        assertEquals(HUGE.add(cache.getBig("a")), cache.getTotalBig());
        assertEquals(Long.MAX_VALUE, cache.getTotal());
        cache.extractBig("b", HUGE.add(BigInteger.ONE));
        cache.extract("a", Long.MAX_VALUE);
        assertEquals(BigInteger.ONE, cache.getTotalBig());
        assertEquals(1L, cache.get("a"));
        cache.extract("a", 1);
        assertTrue(cache.isEmpty());
        assertEquals(BigInteger.ZERO, cache.getTotalBig());
    }

    @Test
    public void partialFlushConservesExactTotalsAndRemovesEmptyEntries() {
        final BigCacheCounter<String> cache = new BigCacheCounter<>();
        cache.insertBig("a", HUGE);
        cache.insertBig("b", HUGE);
        cache
            .updateAllBig((key, amount) -> key.equals("a") ? amount.subtract(BigInteger.valueOf(37)) : BigInteger.ZERO);
        assertEquals(HUGE.subtract(BigInteger.valueOf(37)), cache.getTotalBig());
        assertEquals(BigInteger.ZERO, cache.getBig("b"));
        assertEquals(Long.MAX_VALUE, cache.get("a"));
        cache.clear();
        assertTrue(cache.isEmpty());
        assertEquals(0L, cache.getTotal());
    }

    @Test
    public void invalidFlushDoesNotCorruptTheCurrentEntry() {
        final BigCacheCounter<String> cache = new BigCacheCounter<>();
        cache.insertBig("a", HUGE);
        assertThrows(
            IllegalArgumentException.class,
            () -> cache.updateAllBig((key, amount) -> amount.add(BigInteger.ONE)));
        assertThrows(
            IllegalArgumentException.class,
            () -> cache.updateAllBig((key, amount) -> BigInteger.ONE.negate()));
        assertEquals(HUGE, cache.getTotalBig());
        assertEquals(HUGE, cache.getBig("a"));
    }

    @Test
    public void legacyMutatorRefusesToTruncateAnExactEntry() {
        final BigCacheCounter<String> cache = new BigCacheCounter<>();
        cache.insertBig("a", HUGE);
        assertThrows(ArithmeticException.class, () -> cache.updateAll((key, amount) -> amount - 1));
        assertEquals(HUGE, cache.getTotalBig());
        cache.extractBig("a", HUGE.subtract(BigInteger.valueOf(5)));
        cache.updateAll((key, amount) -> amount - 1);
        assertEquals(4L, cache.getTotal());
    }
}
