package com.silvia.apeiron.api.machine.parallel;

import java.math.BigInteger;
import java.util.Objects;

/** An absent numerical cap, or an exact nonnegative bound; unlimited is never represented by a sentinel integer. */
public final class ParallelLimit {

    private static final ParallelLimit UNLIMITED = new ParallelLimit(null);
    private final BigInteger bound;

    private ParallelLimit(final BigInteger bound) {
        this.bound = bound;
    }

    public static ParallelLimit unlimited() {
        return UNLIMITED;
    }

    public static ParallelLimit bounded(final BigInteger bound) {
        Objects.requireNonNull(bound, "bound");
        if (bound.signum() < 0) throw new IllegalArgumentException("Negative parallel limit");
        return new ParallelLimit(bound);
    }

    public static ParallelLimit bounded(final long bound) {
        return bounded(BigInteger.valueOf(bound));
    }

    public boolean isUnlimited() {
        return bound == null;
    }

    public BigInteger applyTo(final BigInteger resourceBound) {
        Objects.requireNonNull(resourceBound, "resourceBound");
        if (resourceBound.signum() < 0) throw new IllegalArgumentException("Negative resource bound");
        return isUnlimited() ? resourceBound : resourceBound.min(bound);
    }
}
