package com.silvia.apeiron.common.machine.parallel;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Arrays;

import com.silvia.apeiron.api.machine.parallel.ParallelLimit;

/** Exact max-flow allocation prevents wildcard/ore inputs from sharing the same stock twice. */
public final class BigInputAllocation {

    private final BigInteger[] available;
    private final BigInteger[] costs;
    private final boolean[][] matches;
    private final boolean[] renewable;

    public BigInputAllocation(BigInteger[] available, BigInteger[] costs, boolean[][] matches) {
        this(available, costs, matches, new boolean[available.length]);
    }

    public BigInputAllocation(BigInteger[] available, BigInteger[] costs, boolean[][] matches, boolean[] renewable) {
        this.available = available.clone();
        if (renewable.length != available.length) throw new IllegalArgumentException("Invalid renewable resources");
        this.renewable = renewable.clone();
        this.costs = costs.clone();
        this.matches = new boolean[matches.length][];
        if (matches.length != costs.length) throw new IllegalArgumentException("Invalid ingredient matrix");
        for (int i = 0; i < costs.length; i++) {
            if (costs[i].signum() <= 0 || matches[i].length != available.length)
                throw new IllegalArgumentException("Invalid ingredient cost");
            this.matches[i] = matches[i].clone();
        }
        for (BigInteger amount : available)
            if (amount.signum() < 0) throw new IllegalArgumentException("Negative stock");
    }

    public BigInteger maximum(ParallelLimit limit) {
        if (costs.length == 0) return limit.applyTo(BigInteger.ONE);
        BigInteger upper = null;
        for (int i = 0; i < costs.length; i++) {
            BigInteger stock = BigInteger.ZERO;
            boolean infinite = false;
            for (int j = 0; j < available.length; j++) if (matches[i][j]) {
                stock = stock.add(available[j]);
                infinite |= renewable[j];
            }
            if (infinite) continue;
            BigInteger bound = stock.divide(costs[i]);
            upper = upper == null ? bound : upper.min(bound);
        }
        upper = upper == null ? limit.getBound()
            .orElse(BigInteger.ONE) : limit.applyTo(upper);
        if (allocate(upper) != null) return upper;
        BigInteger lower = BigInteger.ZERO;
        while (lower.compareTo(upper) < 0) {
            BigInteger middle = lower.add(upper)
                .add(BigInteger.ONE)
                .shiftRight(1);
            if (allocate(middle) != null) lower = middle;
            else upper = middle.subtract(BigInteger.ONE);
        }
        return lower;
    }

    /** Returns each stock's debit, or null if it cannot satisfy the complete recipe. */
    public BigInteger[] allocate(BigInteger parallels) {
        if (parallels.signum() < 0) throw new IllegalArgumentException("Negative parallels");
        int resourceStart = 1, needStart = resourceStart + available.length, sink = needStart + costs.length;
        BigInteger[][] capacity = new BigInteger[sink + 1][sink + 1];
        for (BigInteger[] row : capacity) Arrays.fill(row, BigInteger.ZERO);
        BigInteger required = BigInteger.ZERO;
        for (BigInteger cost : costs) required = required.add(cost.multiply(parallels));
        for (int j = 0; j < available.length; j++)
            capacity[0][resourceStart + j] = renewable[j] ? required : available[j];
        for (int i = 0; i < costs.length; i++) {
            BigInteger amount = costs[i].multiply(parallels);
            capacity[needStart + i][sink] = amount;
            for (int j = 0; j < available.length; j++)
                if (matches[i][j]) capacity[resourceStart + j][needStart + i] = amount;
        }
        BigInteger flow = BigInteger.ZERO;
        while (flow.compareTo(required) < 0) {
            int[] previous = new int[sink + 1];
            Arrays.fill(previous, -1);
            previous[0] = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(0);
            while (!queue.isEmpty() && previous[sink] < 0) {
                int from = queue.remove();
                for (int to = 1; to <= sink; to++) if (previous[to] < 0 && capacity[from][to].signum() > 0) {
                    previous[to] = from;
                    queue.add(to);
                }
            }
            if (previous[sink] < 0) return null;
            BigInteger moved = required.subtract(flow);
            for (int node = sink; node != 0; node = previous[node]) moved = moved.min(capacity[previous[node]][node]);
            for (int node = sink; node != 0; node = previous[node]) {
                int from = previous[node];
                capacity[from][node] = capacity[from][node].subtract(moved);
                capacity[node][from] = capacity[node][from].add(moved);
            }
            flow = flow.add(moved);
        }
        BigInteger[] debits = new BigInteger[available.length];
        for (int j = 0; j < available.length; j++)
            debits[j] = (renewable[j] ? required : available[j]).subtract(capacity[0][resourceStart + j]);
        return debits;
    }
}
