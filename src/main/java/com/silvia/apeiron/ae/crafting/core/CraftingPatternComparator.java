package com.silvia.apeiron.ae.crafting.core;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import appeng.api.networking.crafting.ICraftingPatternDetails;

/** Preserves AE's task order without treating a hash collision as pattern equality. */
public final class CraftingPatternComparator implements Comparator<ICraftingPatternDetails> {

    // Only unequal patterns with the same priority and hash need a tie breaker. Equality-based keys
    // give separately decoded copies of the same pattern the same rank, unlike identityHashCode.
    private final Map<ICraftingPatternDetails, Long> collisionOrder = new HashMap<>();
    private long nextOrder;

    @Override
    public int compare(ICraftingPatternDetails left, ICraftingPatternDetails right) {
        if (left == right) return 0;
        int result = Integer.compare(left.getPriority(), right.getPriority());
        if (result != 0) return result;
        result = Integer.compare(left.hashCode(), right.hashCode());
        if (result != 0 || left.equals(right)) return result;

        // TreeMap compares a new key against an existing key. Rank the existing key first so a
        // saved, sorted task list keeps its order when inserted into a fresh CPU during reload.
        long rightOrder = orderOf(right);
        return Long.compare(orderOf(left), rightOrder);
    }

    private long orderOf(ICraftingPatternDetails pattern) {
        Long order = collisionOrder.get(pattern);
        if (order == null) {
            order = nextOrder++;
            collisionOrder.put(pattern, order);
        }
        return order;
    }

    /** Call only after all task maps using this comparator have been emptied. */
    public void clear() {
        collisionOrder.clear();
        nextOrder = 0;
    }
}
