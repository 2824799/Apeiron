package com.silvia.apeiron.mixin.ae.crafting.core;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.crafting.core.BigCraftingCPU;
import com.silvia.apeiron.ae.crafting.core.BigCraftingCpuEntries;

import appeng.api.networking.crafting.CraftingItemList;
import appeng.api.storage.data.IAEStack;
import appeng.container.implementations.ContainerCraftingCPU;
import appeng.me.cluster.implementations.CraftingCPUCluster;

/** Supplies exact CPU row values before AE constructs its long-based visual record. */
@Mixin(value = ContainerCraftingCPU.class, remap = false)
public abstract class ContainerCraftingCPUBigMixin {

    @Redirect(
        method = "buildVisualEntryUpdates",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/me/cluster/implementations/CraftingCPUCluster;getStackAmount(Lappeng/api/storage/data/IAEStack;Lappeng/api/networking/crafting/CraftingItemList;)J",
            ordinal = 0))
    private static long apeiron$captureStored(final CraftingCPUCluster monitor, final IAEStack<?> stack,
        final CraftingItemList list) {
        final BigInteger exact = exactAmount(monitor, stack, list);
        BigCraftingCpuEntries.captureSlot(0, exact);
        return BigAEStackValues.saturatedLong(exact);
    }

    @Redirect(
        method = "buildVisualEntryUpdates",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/me/cluster/implementations/CraftingCPUCluster;getStackAmount(Lappeng/api/storage/data/IAEStack;Lappeng/api/networking/crafting/CraftingItemList;)J",
            ordinal = 1))
    private static long apeiron$captureActive(final CraftingCPUCluster monitor, final IAEStack<?> stack,
        final CraftingItemList list) {
        final BigInteger exact = exactAmount(monitor, stack, list);
        BigCraftingCpuEntries.captureSlot(1, exact);
        return BigAEStackValues.saturatedLong(exact);
    }

    @Redirect(
        method = "buildVisualEntryUpdates",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/me/cluster/implementations/CraftingCPUCluster;getStackAmount(Lappeng/api/storage/data/IAEStack;Lappeng/api/networking/crafting/CraftingItemList;)J",
            ordinal = 2))
    private static long apeiron$capturePending(final CraftingCPUCluster monitor, final IAEStack<?> stack,
        final CraftingItemList list) {
        final BigInteger exact = exactAmount(monitor, stack, list);
        BigCraftingCpuEntries.captureSlot(2, exact);
        return BigAEStackValues.saturatedLong(exact);
    }

    private static BigInteger exactAmount(final CraftingCPUCluster monitor, final IAEStack<?> stack,
        final CraftingItemList list) {
        final BigInteger value = monitor instanceof BigCraftingCPU
                ? ((BigCraftingCPU) monitor).getStackAmountBig(stack, list)
                : BigInteger.valueOf(monitor.getStackAmount(stack, list));
        return value;
    }
}
