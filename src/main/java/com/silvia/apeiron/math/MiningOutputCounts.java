package com.silvia.apeiron.math;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Random;

/** Samples a binomial count without creating one ItemStack per roll. */
public final class MiningOutputCounts {

    private MiningOutputCounts() {}

    public static BigInteger binomial(BigInteger trials, int chance, int scale, Random random) {
        if (trials.signum() < 0 || chance < 0 || chance > scale || scale <= 0)
            throw new IllegalArgumentException("Invalid probability");
        if (chance == 0) return BigInteger.ZERO;
        if (chance == scale) return trials;
        if (chance > scale / 2) return trials.subtract(binomial(trials, scale - chance, scale, random));
        if (trials.compareTo(BigInteger.valueOf(4096)) <= 0) {
            int result = 0;
            for (int i = 0; i < trials.intValue(); i++) if (random.nextInt(scale) < chance) result++;
            return BigInteger.valueOf(result);
        }
        BigInteger[] mean = trials.multiply(BigInteger.valueOf(chance))
            .divideAndRemainder(BigInteger.valueOf(scale));
        if (mean[0].compareTo(BigInteger.valueOf(1024)) < 0) {
            long remaining = trials.longValueExact();
            long result = 0;
            double logFailure = Math.log1p(-(double) chance / scale);
            while (remaining > 0) {
                long gap = (long) Math.floor(Math.log1p(-random.nextDouble()) / logFailure);
                if (gap >= remaining) break;
                remaining -= gap + 1;
                result++;
            }
            return BigInteger.valueOf(result);
        }
        BigInteger variance = trials.multiply(BigInteger.valueOf(chance))
            .multiply(BigInteger.valueOf(scale - chance))
            .divide(
                BigInteger.valueOf(scale)
                    .pow(2));
        BigInteger deviation = sqrt(variance);
        BigInteger result = mean[0].add(random.nextInt(scale) < mean[1].intValue() ? BigInteger.ONE : BigInteger.ZERO)
            .add(
                new BigDecimal(deviation).multiply(BigDecimal.valueOf(random.nextGaussian()))
                    .toBigInteger());
        return result.max(BigInteger.ZERO)
            .min(trials);
    }

    private static BigInteger sqrt(BigInteger value) {
        if (value.signum() == 0) return BigInteger.ZERO;
        BigInteger guess = BigInteger.ONE.shiftLeft((value.bitLength() + 1) / 2);
        while (true) {
            BigInteger next = guess.add(value.divide(guess))
                .shiftRight(1);
            if (next.compareTo(guess) >= 0) return guess;
            guess = next;
        }
    }
}
