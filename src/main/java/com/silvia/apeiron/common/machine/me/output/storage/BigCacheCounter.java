// SPDX-License-Identifier: LGPL-3.0-only
// Based on GT5-Unofficial's AECacheCounter, rewritten for arbitrary-precision quantities.
package com.silvia.apeiron.common.machine.me.output.storage;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.ObjLongConsumer;

import com.silvia.apeiron.math.AdaptiveInteger;

/** Exact per-type and total accounting; ordinary increments retain AdaptiveInteger's long fast path. */
public final class BigCacheCounter<T> {

    private final Map<T, AdaptiveInteger> amounts = new LinkedHashMap<>();
    private final AdaptiveInteger total = new AdaptiveInteger(0L);

    public void insert(final T key, final long added) {
        if (added <= 0) return;
        this.amounts.computeIfAbsent(key, ignored -> new AdaptiveInteger(0L))
            .add(added);
        this.total.add(added);
    }

    public void insertBig(final T key, final BigInteger added) {
        if (added.signum() <= 0) return;
        this.amounts.computeIfAbsent(key, ignored -> new AdaptiveInteger(0L))
            .add(added);
        this.total.add(added);
    }

    public void extract(final T key, final long removed) {
        if (removed > 0) this.extractBig(key, BigInteger.valueOf(removed));
    }

    public void extractBig(final T key, final BigInteger removed) {
        if (removed.signum() <= 0) return;
        final AdaptiveInteger old = this.amounts.get(key);
        if (old == null) return;
        final BigInteger taken = old.toBigInteger()
            .min(removed);
        old.subtract(taken);
        this.total.subtract(taken);
        if (old.signum() == 0) this.amounts.remove(key);
    }

    public long getTotal() {
        return this.total.longValueSaturated();
    }

    public BigInteger getTotalBig() {
        return this.total.toBigInteger();
    }

    public long get(final T key) {
        final AdaptiveInteger value = this.amounts.get(key);
        return value == null ? 0L : value.longValueSaturated();
    }

    public BigInteger getBig(final T key) {
        final AdaptiveInteger value = this.amounts.get(key);
        return value == null ? BigInteger.ZERO : value.toBigInteger();
    }

    public boolean isEmpty() {
        return this.amounts.isEmpty();
    }

    public int size() {
        return this.amounts.size();
    }

    public void clear() {
        this.amounts.clear();
        this.total.set(0L);
    }

    @FunctionalInterface
    public interface EntryProcessor<T> {

        long apply(T key, long amount);
    }

    @FunctionalInterface
    public interface BigEntryProcessor<T> {

        BigInteger apply(T key, BigInteger amount);
    }

    public void updateAll(final EntryProcessor<T> processor) {
        this.updateAllBig((key, amount) -> {
            if (!AdaptiveInteger.fitsLong(amount)) {
                throw new ArithmeticException("Use updateAllBig for an arbitrary-precision cache entry");
            }
            return BigInteger.valueOf(processor.apply(key, amount.longValueExact()));
        });
    }

    public void updateAllBig(final BigEntryProcessor<T> processor) {
        final Iterator<Map.Entry<T, AdaptiveInteger>> iterator = this.amounts.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            final Map.Entry<T, AdaptiveInteger> entry = iterator.next();
            final BigInteger old = entry.getValue()
                .toBigInteger();
            final BigInteger remaining = processor.apply(entry.getKey(), old);
            if (remaining.signum() < 0 || remaining.compareTo(old) > 0) {
                throw new IllegalArgumentException("Cache remainder must be between zero and the original amount");
            }
            this.total.subtract(old.subtract(remaining));
            if (remaining.signum() == 0) iterator.remove();
            else entry.getValue()
                .set(remaining);
        }
    }

    public void iterateAll(final ObjLongConsumer<T> consumer) {
        this.amounts.forEach((key, amount) -> consumer.accept(key, amount.longValueSaturated()));
    }

    public void iterateAllBig(final BiConsumer<T, BigInteger> consumer) {
        this.amounts.forEach((key, amount) -> consumer.accept(key, amount.toBigInteger()));
    }
}
