package com.silvia.apeiron.api.machine.tst;

import java.math.BigInteger;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;

/** Exact additions to TST's shared long ME output API. Existing method names and signatures are retained. */
public interface BigTstOutputController {

    void mergeItemIntoMEOutputQueueBig(ItemStack type, BigInteger amount);

    void mergeFluidIntoMEOutputQueueBig(FluidStack type, BigInteger amount);

    BigInteger outputItemToMENetworkBig(ItemStack type, BigInteger amount);

    BigInteger outputFluidToMENetworkBig(FluidStack type, BigInteger amount);

    BigInteger getRecipeItemOutputBig();

    BigInteger getRecipeFluidOutputBig();

    void copyRecipeOutputsBig(BigMachineOutputQueue target);

    BigInteger getPendingItemOutputBig();

    BigInteger getPendingFluidOutputBig();

    void flushOutputsBig();

    void saveProducedOutputsBig(NBTTagCompound tag);
}
