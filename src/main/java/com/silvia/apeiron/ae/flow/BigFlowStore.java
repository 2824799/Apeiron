package com.silvia.apeiron.ae.flow;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.data.IAEStack;
import com.silvia.apeiron.ae.stack.BigAEStackValues;

/** Exact side-channel storage for the rolling item-flow window. */
public final class BigFlowStore {

    public static final class Totals {

        private BigInteger in = BigInteger.ZERO;
        private BigInteger out = BigInteger.ZERO;

        public BigInteger in() {
            return this.in;
        }

        public BigInteger out() {
            return this.out;
        }

        private void addIn(final BigInteger amount) {
            this.in = this.in.add(amount);
        }

        private void addOut(final BigInteger amount) {
            this.out = this.out.add(amount);
        }
    }

    private static final class Bucket {

        private final long start;
        private final Map<IAEStack<?>, Totals> byItem = new HashMap<>();

        private Bucket(final long start) {
            this.start = start;
        }
    }

    private final Deque<Bucket> buckets = new ArrayDeque<>();

    public void record(final IAEStack<?> diff, final BaseActionSource source) {
        if (!(source instanceof MachineSource) || diff == null) return;
        final BigInteger amount = BigAEStackValues.get(diff);
        if (amount.signum() == 0) return;

        final long now = System.currentTimeMillis();
        Bucket bucket = this.buckets.peekLast();
        if (bucket == null || now - bucket.start >= 200L) {
            bucket = new Bucket(now);
            this.buckets.addLast(bucket);
        }

        final Totals totals = bucket.byItem.computeIfAbsent(diff.copy(), ignored -> new Totals());
        if (amount.signum() < 0) totals.addOut(amount.negate());
        else totals.addIn(amount);
    }

    public Map<IAEStack<?>, Totals> totals() {
        final Map<IAEStack<?>, Totals> result = new HashMap<>();
        for (final Bucket bucket : this.buckets) {
            for (final Map.Entry<IAEStack<?>, Totals> entry : bucket.byItem.entrySet()) {
                final Totals total = result.computeIfAbsent(entry.getKey(), ignored -> new Totals());
                total.addIn(entry.getValue().in());
                total.addOut(entry.getValue().out());
            }
        }
        return result;
    }

    public void purge(final int windowMinutes) {
        final long cutoff = System.currentTimeMillis() - Math.max(1, windowMinutes) * 60_000L;
        while (!this.buckets.isEmpty() && this.buckets.peekFirst().start + 200L <= cutoff) {
            this.buckets.pollFirst();
        }
    }

    public void clear() {
        this.buckets.clear();
    }
}
