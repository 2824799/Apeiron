package com.silvia.apeiron.mixin.ae.crafting.core;

import java.math.BigInteger;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.silvia.apeiron.ae.crafting.core.BigCraftingTracker;
import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.api.config.InsertionMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.helpers.MultiCraftingTracker;
import appeng.util.InventoryAdaptor;

/** Keeps interface and export-bus crafting requests chunked at the physical inventory boundary. */
@Mixin(value = MultiCraftingTracker.class, remap = false)
public abstract class MultiCraftingTrackerMixin implements BigCraftingTracker {

    private static final BigInteger MAX_PHYSICAL_REQUEST = BigInteger.valueOf(Integer.MAX_VALUE - 1L);

    @Shadow
    public abstract boolean handleCrafting(int slot, long itemToCraft, IAEStack<?> stack, World world, IGrid grid,
        ICraftingGrid craftingGrid, BaseActionSource source);

    @Override
    public boolean handleCraftingBig(final int slot, final BigInteger amount, final IAEStack<?> stack,
        final InventoryAdaptor adaptor, final World world, final IGrid grid, final ICraftingGrid craftingGrid,
        final BaseActionSource source) {
        if (amount == null || amount.signum() <= 0 || stack == null) return false;

        BigInteger physicalLimit = MAX_PHYSICAL_REQUEST;
        if (stack instanceof IAEItemStack) {
            physicalLimit = BigInteger.valueOf(
                ((IAEItemStack) stack).getItemStack()
                    .getMaxStackSize());
        }
        final BigInteger requestAmount = amount.min(physicalLimit);
        final IAEStack<?> request = BigAEStackValues.copyWithSize(stack, requestAmount);
        if (adaptor.simulateAddStack(request, InsertionMode.DEFAULT) != null) return false;

        return handleCrafting(slot, requestAmount.longValue(), request, world, grid, craftingGrid, source);
    }
}
