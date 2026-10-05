package com.silvia.apeiron.math;

import java.math.BigInteger;

/** The native multiplier is 2 * ceil(arrays^1.5), with no intermediate int or double rounding. */
public final class StarcoreYield {

    private StarcoreYield() {}

    public static BigInteger amount(int base, int arrays) {
        if (base < 1 || arrays < 0) throw new IllegalArgumentException("Invalid mining yield");
        if (arrays == 0) return BigInteger.valueOf(base);
        BigInteger cube = BigInteger.valueOf(arrays)
            .pow(3);
        BigInteger low = BigInteger.ZERO;
        BigInteger high = BigInteger.ONE.shiftLeft((cube.bitLength() + 1) / 2);
        while (high.subtract(low)
            .compareTo(BigInteger.ONE) > 0) {
            BigInteger middle = low.add(high)
                .shiftRight(1);
            if (middle.multiply(middle)
                .compareTo(cube) >= 0) high = middle;
            else low = middle;
        }
        return BigInteger.valueOf(base)
            .multiply(high)
            .shiftLeft(1);
    }
}
