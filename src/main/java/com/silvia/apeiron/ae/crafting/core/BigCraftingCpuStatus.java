package com.silvia.apeiron.ae.crafting.core;

import java.math.BigInteger;

import appeng.container.implementations.CraftingCPUStatus;

/** Exact quantities carried by a CPU selector status record. */
public interface BigCraftingCpuStatus {

    BigInteger getStorageBig();

    BigInteger getUsedStorageBig();

    BigInteger getTotalItemsBig();

    BigInteger getRemainingItemsBig();

    static BigInteger storage(final CraftingCPUStatus status) {
        return status instanceof BigCraftingCpuStatus
                ? ((BigCraftingCpuStatus) status).getStorageBig()
                : BigInteger.valueOf(status.getStorage());
    }

    static BigInteger usedStorage(final CraftingCPUStatus status) {
        return status instanceof BigCraftingCpuStatus
                ? ((BigCraftingCpuStatus) status).getUsedStorageBig()
                : BigInteger.valueOf(status.getUsedStorage());
    }

    static BigInteger totalItems(final CraftingCPUStatus status) {
        return status instanceof BigCraftingCpuStatus
                ? ((BigCraftingCpuStatus) status).getTotalItemsBig()
                : BigInteger.valueOf(status.getTotalItems());
    }

    static BigInteger remainingItems(final CraftingCPUStatus status) {
        return status instanceof BigCraftingCpuStatus
                ? ((BigCraftingCpuStatus) status).getRemainingItemsBig()
                : BigInteger.valueOf(status.getRemainingItems());
    }
}
