package com.silvia.apeiron.math;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Applies a machine's decimal yield before converting to an integer, without a long or double intermediate count. */
public final class BigOutputScaling {

    private BigOutputScaling() {}

    public static BigInteger scale(final BigInteger base, final double yield, final long parallels) {
        if (base.signum() < 0 || parallels < 0 || !Double.isFinite(yield) || yield < 0) {
            throw new IllegalArgumentException("Invalid output quantity, yield or successful parallel count");
        }
        return new BigDecimal(base).multiply(BigDecimal.valueOf(yield))
            .multiply(BigDecimal.valueOf(parallels))
            .toBigInteger();
    }
}
