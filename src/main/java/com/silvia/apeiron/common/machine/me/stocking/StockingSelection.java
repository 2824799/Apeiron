// SPDX-License-Identifier: GPL-3.0-only
package com.silvia.apeiron.common.machine.me.stocking;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.api.storage.data.IAEStack;

/** Borrow the shared ME lists; copy only the chosen types and retain at most the configured slot count. */
final class StockingSelection {

    private StockingSelection() {}

    static List<IAEStack<?>> select(StockingFilter filter, Iterable<? extends IAEStack<?>> items,
        Iterable<? extends IAEStack<?>> fluids) {
        List<IAEStack<?>> result = new ArrayList<>();
        if (filter.getPriority() == StockingFilter.Priority.NETWORK) {
            for (Iterable<? extends IAEStack<?>> source : sources(items, fluids)) {
                if (result.size() >= filter.getTypeLimit()) break;
                if (source == null) continue;
                for (IAEStack<?> stack : source) {
                    if (result.size() >= filter.getTypeLimit()) break;
                    if (filter.accepts(stack)) result.add(stack.copy());
                }
            }
            return result;
        }
        Comparator<Entry> order = (a, b) -> {
            int quantity = a.infinite == b.infinite ? a.infinite ? 0 : a.amount.compareTo(b.amount)
                : a.infinite ? 1 : -1;
            if (filter.getPriority() == StockingFilter.Priority.MOST) quantity = -quantity;
            return quantity == 0 ? Long.compare(a.index, b.index) : quantity;
        };
        PriorityQueue<Entry> chosen = new PriorityQueue<>(filter.getTypeLimit(), order.reversed());
        long index = 0;
        for (Iterable<? extends IAEStack<?>> source : sources(items, fluids)) {
            if (source == null) continue;
            for (IAEStack<?> stack : source) {
                if (!filter.accepts(stack)) continue;
                Entry candidate = new Entry(stack, index++);
                if (chosen.size() < filter.getTypeLimit()) chosen.add(candidate);
                else if (order.compare(candidate, chosen.peek()) < 0) {
                    chosen.poll();
                    chosen.add(candidate);
                }
            }
        }
        List<Entry> sorted = new ArrayList<>(chosen);
        sorted.sort(order);
        for (Entry entry : sorted) result.add(entry.stack.copy());
        return result;
    }

    private static List<Iterable<? extends IAEStack<?>>> sources(Iterable<? extends IAEStack<?>> items,
        Iterable<? extends IAEStack<?>> fluids) {
        return java.util.Arrays.asList(items, fluids);
    }

    private static final class Entry {

        private final IAEStack<?> stack;
        private final BigInteger amount;
        private final boolean infinite;
        private final long index;

        private Entry(IAEStack<?> stack, long index) {
            this.stack = stack;
            this.index = index;
            amount = BigAEStackValues.get(stack);
            infinite = BigAEStackValues.isInfinite(stack);
        }
    }
}
