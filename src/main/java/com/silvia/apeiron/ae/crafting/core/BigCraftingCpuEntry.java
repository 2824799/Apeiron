package com.silvia.apeiron.ae.crafting.core;

import java.math.BigInteger;

import appeng.container.implementations.CraftingCpuEntry;

/** Exact quantities carried by one crafting CPU terminal entry. */
public interface BigCraftingCpuEntry {

    BigInteger getStoredAmountBig();

    BigInteger getActiveAmountBig();

    BigInteger getPendingAmountBig();

    BigInteger getTotalAmountBig();

    static BigInteger stored(final CraftingCpuEntry entry) {
        return entry instanceof BigCraftingCpuEntry
                ? ((BigCraftingCpuEntry) entry).getStoredAmountBig()
                : BigInteger.valueOf(entry.getStoredAmount());
    }

    static BigInteger active(final CraftingCpuEntry entry) {
        return entry instanceof BigCraftingCpuEntry
                ? ((BigCraftingCpuEntry) entry).getActiveAmountBig()
                : BigInteger.valueOf(entry.getActiveAmount());
    }

    static BigInteger pending(final CraftingCpuEntry entry) {
        return entry instanceof BigCraftingCpuEntry
                ? ((BigCraftingCpuEntry) entry).getPendingAmountBig()
                : BigInteger.valueOf(entry.getPendingAmount());
    }
}
