package com.silvia.apeiron.ae.crafting.core;

import java.math.BigInteger;
import java.util.Map;

import appeng.api.networking.crafting.CraftingItemList;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.me.cluster.implementations.CraftingCPUCluster;

/** Exact-count view of the quantities kept by an AE crafting CPU. */
public interface BigCraftingCPU {

    BigInteger getStackAmountBig(IAEStack<?> stack, CraftingItemList list);

    void addCraftingBig(ICraftingPatternDetails details, BigInteger crafts);

    Map<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> getTaskEntriesBig();

    void postCraftingStatusChangeBig(IAEStack<?> stack);

    void completeJobBig();
}
