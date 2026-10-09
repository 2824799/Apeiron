package com.silvia.apeiron.common.machine.parallel;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.function.Predicate;

import com.silvia.apeiron.api.machine.parallel.ParallelLimit;

/** Plans mixed transformations against one shared energy budget, parallel cap and output preflight. */
public final class ItemProcessingPlan {

    private final BigInteger[] debits;
    private final BigInteger parallels;
    private final BigInteger totalEU;

    private ItemProcessingPlan(BigInteger[] debits, BigInteger parallels, BigInteger totalEU) {
        this.debits = debits;
        this.parallels = parallels;
        this.totalEU = totalEU;
    }

    public static ItemProcessingPlan calculate(BigInteger[] available, boolean[] renewable, BigInteger[] costs,
        ParallelLimit limit, BigInteger availableEU, Predicate<BigInteger[]> outputsFit) {
        // Check the affordable batch once before falling back to per-input capacity searches.
        // Rebuilding every growing prefix makes large mixed inventories quadratic even with free output space.
        ItemProcessingPlan candidate = plan(available, renewable, costs, limit, availableEU, null);
        if (candidate.parallels.signum() == 0 || outputsFit.test(candidate.getDebits())) return candidate;
        return plan(available, renewable, costs, limit, availableEU, outputsFit);
    }

    private static ItemProcessingPlan plan(BigInteger[] available, boolean[] renewable, BigInteger[] costs,
        ParallelLimit limit, BigInteger availableEU, Predicate<BigInteger[]> outputsFit) {
        if (available.length != renewable.length || available.length != costs.length || availableEU.signum() < 0)
            throw new IllegalArgumentException("Invalid item processing inventory");
        BigInteger[] debits = new BigInteger[available.length];
        Arrays.fill(debits, BigInteger.ZERO);
        BigInteger parallels = BigInteger.ZERO, energy = BigInteger.ZERO;
        for (int i = 0; i < available.length; i++) {
            if (available[i].signum() < 0 || costs[i] != null && costs[i].signum() < 0)
                throw new IllegalArgumentException("Negative item processing quantity");
            if (costs[i] == null) continue;
            BigInteger maximum = renewable[i] ? null : available[i];
            if (!limit.isUnlimited()) {
                BigInteger remaining = limit.getBound()
                    .get()
                    .subtract(parallels)
                    .max(BigInteger.ZERO);
                maximum = maximum == null ? remaining : maximum.min(remaining);
            }
            if (costs[i].signum() > 0) {
                BigInteger affordable = availableEU.subtract(energy)
                    .divide(costs[i]);
                maximum = maximum == null ? affordable : maximum.min(affordable);
            }
            if (maximum == null) throw new IllegalArgumentException("Renewable free input needs a finite parallel cap");
            debits[i] = maximum;
            if (maximum.signum() > 0 && outputsFit != null && !outputsFit.test(debits.clone())) {
                BigInteger lower = BigInteger.ZERO, upper = maximum;
                while (lower.compareTo(upper) < 0) {
                    BigInteger middle = lower.add(upper)
                        .add(BigInteger.ONE)
                        .shiftRight(1);
                    debits[i] = middle;
                    if (outputsFit.test(debits.clone())) lower = middle;
                    else upper = middle.subtract(BigInteger.ONE);
                }
                debits[i] = lower;
            }
            parallels = parallels.add(debits[i]);
            energy = energy.add(costs[i].multiply(debits[i]));
        }
        return new ItemProcessingPlan(debits, parallels, energy);
    }

    public BigInteger[] getDebits() {
        return debits.clone();
    }

    public BigInteger getParallelsBig() {
        return parallels;
    }

    public BigInteger getTotalEUBig() {
        return totalEU;
    }
}
