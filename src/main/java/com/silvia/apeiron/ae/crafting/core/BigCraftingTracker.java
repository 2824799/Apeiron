package com.silvia.apeiron.ae.crafting.core;

import java.math.BigInteger;

import net.minecraft.world.World;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.util.InventoryAdaptor;

/** Exact-count entry point for AE's per-slot crafting requester tracker. */
public interface BigCraftingTracker {

    boolean handleCraftingBig(int slot, BigInteger amount, IAEStack<?> stack, InventoryAdaptor adaptor,
        World world, IGrid grid, ICraftingGrid craftingGrid, BaseActionSource source);
}
