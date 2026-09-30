package com.silvia.apeiron.math;

import java.math.BigInteger;
import java.util.Objects;

/** A mutable signed integer that allocates a BigInteger only after a long overflows. */
public final class AdaptiveInteger implements Comparable<AdaptiveInteger> {

    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private long small;
    private BigInteger big;

    public AdaptiveInteger(long value) {
        this.small = value;
    }

    public AdaptiveInteger(BigInteger value) {
        set(value);
    }

    public void set(long value) {
        small = value;
        big = null;
    }

    public void set(BigInteger value) {
        Objects.requireNonNull(value, "value");
        if (fitsLong(value)) {
            set(value.longValue());
        } else {
            small = 0;
            big = value;
        }
    }

    public void add(long amount) {
        if (big != null) {
            set(big.add(BigInteger.valueOf(amount)));
            return;
        }

        long result = small + amount;
        if (((small ^ result) & (amount ^ result)) < 0) {
            set(
                BigInteger.valueOf(small)
                    .add(BigInteger.valueOf(amount)));
        } else {
            small = result;
        }
    }

    public void add(BigInteger amount) {
        Objects.requireNonNull(amount, "amount");
        if (big != null) {
            set(big.add(amount));
        } else if (fitsLong(amount)) {
            add(amount.longValue());
        } else {
            set(
                BigInteger.valueOf(small)
                    .add(amount));
        }
    }

    public void subtract(long amount) {
        if (big != null) {
            set(big.subtract(BigInteger.valueOf(amount)));
            return;
        }

        long result = small - amount;
        if (((small ^ amount) & (small ^ result)) < 0) {
            set(
                BigInteger.valueOf(small)
                    .subtract(BigInteger.valueOf(amount)));
        } else {
            small = result;
        }
    }

    public void subtract(BigInteger amount) {
        Objects.requireNonNull(amount, "amount");
        if (big != null) {
            set(big.subtract(amount));
        } else if (fitsLong(amount)) {
            subtract(amount.longValue());
        } else {
            set(
                BigInteger.valueOf(small)
                    .subtract(amount));
        }
    }

    public void multiply(long factor) {
        if (big != null) {
            set(big.multiply(BigInteger.valueOf(factor)));
            return;
        }
        try {
            small = Math.multiplyExact(small, factor);
        } catch (ArithmeticException overflow) {
            set(
                BigInteger.valueOf(small)
                    .multiply(BigInteger.valueOf(factor)));
        }
    }

    public void multiply(BigInteger factor) {
        Objects.requireNonNull(factor, "factor");
        if (big != null) {
            set(big.multiply(factor));
        } else if (fitsLong(factor)) {
            multiply(factor.longValue());
        } else {
            set(
                BigInteger.valueOf(small)
                    .multiply(factor));
        }
    }

    public boolean isBig() {
        return big != null;
    }

    public int signum() {
        return big == null ? Long.compare(small, 0L) : big.signum();
    }

    public long longValueSaturated() {
        return big == null ? small : big.signum() >= 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
    }

    public long longValueExact() {
        if (big != null) {
            throw new ArithmeticException("integer does not fit in long");
        }
        return small;
    }

    public BigInteger toBigInteger() {
        return big == null ? BigInteger.valueOf(small) : big;
    }

    public AdaptiveInteger copy() {
        return big == null ? new AdaptiveInteger(small) : new AdaptiveInteger(big);
    }

    @Override
    public int compareTo(AdaptiveInteger other) {
        Objects.requireNonNull(other, "other");
        return big == null && other.big == null ? Long.compare(small, other.small)
            : toBigInteger().compareTo(other.toBigInteger());
    }

    @Override
    public String toString() {
        return big == null ? Long.toString(small) : big.toString();
    }

    public static boolean fitsLong(BigInteger value) {
        return value.compareTo(LONG_MIN) >= 0 && value.compareTo(LONG_MAX) <= 0;
    }
}
