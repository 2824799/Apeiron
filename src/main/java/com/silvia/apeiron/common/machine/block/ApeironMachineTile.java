package com.silvia.apeiron.common.machine.block;

import java.util.ArrayList;

import net.minecraft.item.ItemStack;

import com.silvia.apeiron.common.machine.registration.ApeironMachines;

import gregtech.api.metatileentity.BaseMetaTileEntity;

/** Keeps harvested machines in Apeiron's item registry while preserving GregTech's cover and upgrade NBT. */
public final class ApeironMachineTile extends BaseMetaTileEntity {

    @Override
    public ArrayList<ItemStack> getDrops() {
        java.math.BigInteger items = java.math.BigInteger.ZERO;
        java.math.BigInteger fluids = java.math.BigInteger.ZERO;
        int patterns = 0;
        long installed = 0;
        gregtech.api.interfaces.metatileentity.IMetaTileEntity machine = getMetaTileEntity();
        if (machine instanceof com.silvia.apeiron.common.machine.me.stocking.StockingInputHost) {
            com.silvia.apeiron.common.machine.me.stocking.StockingInputLogic stock = ((com.silvia.apeiron.common.machine.me.stocking.StockingInputHost) machine)
                .getStockingInput();
            items = stock.getRefundAmount(false);
            fluids = stock.getRefundAmount(true);
        }
        if (machine instanceof com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputBus)
            items = ((com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputBus) machine).getProvider()
                .getCachedAmountBig();
        if (machine instanceof com.silvia.apeiron.common.machine.me.output.MTEBeamlineMEOutputHatch)
            items = ((com.silvia.apeiron.common.machine.me.output.MTEBeamlineMEOutputHatch) machine)
                .getStoredParticleAmount();
        if (machine instanceof com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputHatch)
            fluids = ((com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputHatch) machine).getProvider()
                .getCachedAmountBig();
        if (machine instanceof com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly)
            fluids = ((com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly) machine)
                .getFluidProvider()
                .getCachedAmountBig();
        if (machine instanceof com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly) {
            com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly input = (com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly) machine;
            for (com.silvia.apeiron.common.machine.me.input.storage.BigPatternBuffer buffer : input.getBuffers()) {
                buffer.reconcile();
                items = items.add(buffer.getItemAmountBig());
                fluids = fluids.add(buffer.getFluidAmountBig());
            }
            for (int slot = 0; slot < input.getPatterns()
                .getSizeInventory(); slot++)
                if (input.getPatterns()
                    .getStackInSlot(slot) != null) patterns++;
        }
        boolean inventory = false;
        ItemStack[] physical = machine == null ? new ItemStack[0] : machine.getRealInventory();
        for (int slot = 0; slot < physical.length; slot++) if (physical[slot] != null && physical[slot].stackSize > 0) {
            inventory = true;
            if (!(machine instanceof com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly)
                || slot >= com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly.PATTERN_COUNT)
                installed += Math.max(0, physical[slot].stackSize);
        }
        boolean keep = inventory || items.signum() > 0 || fluids.signum() > 0;
        // Covers and upgrades are real installed items and must survive dismantling too.
        for (net.minecraftforge.common.util.ForgeDirection side : net.minecraftforge.common.util.ForgeDirection.VALID_DIRECTIONS)
            keep |= hasCoverAtSide(side);
        keep |= getUpgradeCount() > 0;
        final ArrayList<ItemStack> drops = super.getDrops();
        for (int i = 0; i < drops.size(); i++) {
            final ItemStack original = drops.get(i);
            final ItemStack own = new ItemStack(ApeironMachines.block, original.stackSize, original.getItemDamage());
            if (original.hasTagCompound()) {
                final ItemStack normalized = new ItemStack(
                    ApeironMachines.block,
                    original.stackSize,
                    original.getItemDamage());
                normalized.setTagCompound(
                    (net.minecraft.nbt.NBTTagCompound) original.getTagCompound()
                        .copy());
                MachineItemNbt.normalize(normalized);
                if (normalized.hasTagCompound()) own.setTagCompound(normalized.getTagCompound());
            }
            if (keep) {
                if (!own.hasTagCompound()) own.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
                net.minecraft.nbt.NBTTagCompound contents = new net.minecraft.nbt.NBTTagCompound();
                if (items.signum() > 0)
                    contents.setString("items", com.silvia.apeiron.math.BigNumberFormatter.formatExact(items));
                if (fluids.signum() > 0)
                    contents.setString("fluids", com.silvia.apeiron.math.BigNumberFormatter.formatExact(fluids));
                if (patterns > 0) contents.setString("patterns", Integer.toString(patterns));
                if (installed > 0) contents.setString("installed", Long.toString(installed));
                if (!contents.hasNoTags()) own.getTagCompound()
                    .setTag("ApeironContents", contents);
            }
            if (own.hasTagCompound() && own.getTagCompound()
                .hasNoTags()) own.setTagCompound(null);
            drops.set(i, own);
        }
        return drops;
    }
}
