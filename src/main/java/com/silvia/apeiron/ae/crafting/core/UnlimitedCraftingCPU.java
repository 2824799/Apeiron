package com.silvia.apeiron.ae.crafting.core;

/** Infinity is a capability, never a very large finite byte or operation count. */
public interface UnlimitedCraftingCPU {

    boolean isCraftingStorageUnlimited();

    boolean isCraftingParallelUnlimited();
}
