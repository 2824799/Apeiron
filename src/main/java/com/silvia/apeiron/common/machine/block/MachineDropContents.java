package com.silvia.apeiron.common.machine.block;

import java.math.BigInteger;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.input.storage.BigPatternBuffer;
import com.silvia.apeiron.common.machine.me.output.MTEBeamlineMEOutputHatch;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputBus;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputHatch;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.me.stocking.StockingInputHost;
import com.silvia.apeiron.common.machine.me.stocking.StockingInputLogic;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.math.BigNumberFormatter;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/** Captures tooltip counts before GregTech destroys the tile; its native drop writer preserves the actual contents. */
public final class MachineDropContents {

    private MachineDropContents() {}

    public static NBTTagCompound capture(BaseMetaTileEntity tile) {
        BigInteger items = BigInteger.ZERO;
        BigInteger fluids = BigInteger.ZERO;
        int patterns = 0;
        long installed = 0;
        IMetaTileEntity machine = tile.getMetaTileEntity();
        if (machine instanceof StockingInputHost) {
            StockingInputLogic stock = ((StockingInputHost) machine).getStockingInput();
            items = stock.getRefundAmount(false);
            fluids = stock.getRefundAmount(true);
        }
        if (machine instanceof MTEBoundlessMEOutputBus) items = ((MTEBoundlessMEOutputBus) machine).getProvider()
            .getCachedAmountBig();
        if (machine instanceof MTEBeamlineMEOutputHatch)
            items = ((MTEBeamlineMEOutputHatch) machine).getStoredParticleAmount();
        if (machine instanceof MTEBoundlessMEOutputHatch) fluids = ((MTEBoundlessMEOutputHatch) machine).getProvider()
            .getCachedAmountBig();
        if (machine instanceof MTEInfiniteMEOutputAssembly)
            fluids = ((MTEInfiniteMEOutputAssembly) machine).getFluidProvider()
                .getCachedAmountBig();
        if (machine instanceof MTEInfinitePatternInputAssembly) {
            MTEInfinitePatternInputAssembly input = (MTEInfinitePatternInputAssembly) machine;
            for (BigPatternBuffer buffer : input.getBuffers()) {
                buffer.reconcile();
                items = items.add(buffer.getItemAmountBig());
                fluids = fluids.add(buffer.getFluidAmountBig());
            }
            for (int slot = 0; slot < input.getPatterns()
                .getSizeInventory(); slot++)
                if (input.getPatterns()
                    .getStackInSlot(slot) != null) patterns++;
        }
        ItemStack[] inventory = machine == null ? new ItemStack[0] : machine.getRealInventory();
        for (int slot = 0; slot < inventory.length; slot++) {
            ItemStack stack = inventory[slot];
            if (stack != null && stack.stackSize > 0
                && (!(machine instanceof MTEInfinitePatternInputAssembly)
                    || slot >= MTEInfinitePatternInputAssembly.PATTERN_COUNT))
                installed += stack.stackSize;
        }
        NBTTagCompound contents = new NBTTagCompound();
        if (items.signum() > 0) contents.setString("items", BigNumberFormatter.formatExact(items));
        if (fluids.signum() > 0) contents.setString("fluids", BigNumberFormatter.formatExact(fluids));
        if (patterns > 0) contents.setString("patterns", Integer.toString(patterns));
        if (installed > 0) contents.setString("installed", Long.toString(installed));
        return contents;
    }

    public static void finish(List<ItemStack> drops, int machineId, NBTTagCompound contents) {
        for (ItemStack drop : drops) {
            if (!ApeironMachines.isMachineStack(drop) || drop.getItemDamage() != machineId) continue;
            MachineItemNbt.migrate(drop);
            if (!contents.hasNoTags()) {
                if (!drop.hasTagCompound()) drop.setTagCompound(new NBTTagCompound());
                drop.getTagCompound()
                    .setTag("ApeironContents", contents.copy());
            } else if (drop.hasTagCompound()) {
                drop.getTagCompound()
                    .removeTag("ApeironContents");
                if (drop.getTagCompound()
                    .hasNoTags()) drop.setTagCompound(null);
            }
        }
    }
}
