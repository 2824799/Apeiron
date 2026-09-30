package com.silvia.apeiron.bench;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** A small, standalone model of the count-update hot path; no Minecraft classes are involved. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class NumberBenchmark {

    @State(Scope.Thread)
    public static class SmallState {

        long primitive;
        BigInteger big;
        HybridCount hybrid;
        PositiveInt128 int128;
        long delta;
        BigInteger bigDelta;

        @Setup(Level.Iteration)
        public void reset() {
            primitive = 1_000_000L;
            big = BigInteger.valueOf(primitive);
            hybrid = new HybridCount(primitive);
            int128 = new PositiveInt128(primitive, 0L);
            delta = 1_000_003L; // Deliberately outside BigInteger.valueOf's small-value cache.
            bigDelta = BigInteger.valueOf(delta);
        }
    }

    @State(Scope.Thread)
    public static class LargeState {

        BigInteger big;
        HybridCount hybrid;
        PositiveInt128 int128;
        long delta;

        @Setup(Level.Iteration)
        public void reset() {
            big = BigInteger.ONE.shiftLeft(80);
            hybrid = new HybridCount(big);
            int128 = new PositiveInt128(0L, 1L << 16);
            delta = 1_000_003L;
        }
    }

    @Benchmark
    public long longAdd(SmallState state) {
        state.primitive += state.delta;
        return state.primitive;
    }

    @Benchmark
    public long bigIntegerSmallCachedDelta(SmallState state) {
        state.big = state.big.add(state.bigDelta);
        return state.big.longValue();
    }

    @Benchmark
    public long bigIntegerSmallFromLong(SmallState state) {
        state.big = state.big.add(BigInteger.valueOf(state.delta));
        return state.big.longValue();
    }

    @Benchmark
    public long hybridSmall(SmallState state) {
        state.hybrid.add(state.delta);
        return state.hybrid.longValue();
    }

    @Benchmark
    public long int128Small(SmallState state) {
        state.int128.add(state.delta);
        return state.int128.longValue();
    }

    @Benchmark
    public int bigIntegerLargeFromLong(LargeState state) {
        state.big = state.big.add(BigInteger.valueOf(state.delta));
        return state.big.bitLength();
    }

    @Benchmark
    public int hybridLarge(LargeState state) {
        state.hybrid.add(state.delta);
        return state.hybrid.bitLength();
    }

    @Benchmark
    public int int128Large(LargeState state) {
        state.int128.add(state.delta);
        return state.int128.bitLength();
    }

    /** Nonnegative signed 128-bit value, stored as a high and low 64-bit word. */
    private static final class PositiveInt128 {

        private long low;
        private long high;

        private PositiveInt128(long low, long high) {
            this.low = low;
            this.high = high;
        }

        private void add(long amount) {
            if (amount < 0L) {
                throw new IllegalArgumentException("amount must be nonnegative");
            }
            long nextLow = low + amount;
            if (Long.compareUnsigned(nextLow, low) < 0) {
                if (high == Long.MAX_VALUE) {
                    throw new ArithmeticException("signed 128-bit overflow");
                }
                high++;
            }
            low = nextLow;
        }

        private long longValue() {
            return high != 0L || low < 0L ? Long.MAX_VALUE : low;
        }

        private int bitLength() {
            return high != 0L ? 128 - Long.numberOfLeadingZeros(high)
                : 64 - Long.numberOfLeadingZeros(low);
        }
    }

    /** Minimal representative of a mutable counter that promotes on long overflow. */
    private static final class HybridCount {

        private long small;
        private BigInteger large;

        private HybridCount(long small) {
            this.small = small;
        }

        private HybridCount(BigInteger large) {
            this.large = large;
        }

        private void add(long amount) {
            if (large != null) {
                large = large.add(BigInteger.valueOf(amount));
            } else if (small <= Long.MAX_VALUE - amount) {
                small += amount;
            } else {
                large = BigInteger.valueOf(small).add(BigInteger.valueOf(amount));
                small = 0;
            }
        }

        private long longValue() {
            return large == null ? small : Long.MAX_VALUE;
        }

        private int bitLength() {
            return large == null ? BigInteger.valueOf(small).bitLength() : large.bitLength();
        }
    }
}
