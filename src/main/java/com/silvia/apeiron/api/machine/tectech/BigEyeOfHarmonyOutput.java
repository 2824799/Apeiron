package com.silvia.apeiron.api.machine.tectech;

import java.math.BigInteger;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Exact output entry points alongside the controller's original long methods. */
public interface BigEyeOfHarmonyOutput {

    void outputItemToAENetworkBig(ItemStack type, BigInteger amount);

    void outputFluidToAENetworkBig(FluidStack type, BigInteger amount);

    void flushOutputsBig();

    BigInteger getPendingItemOutputBig();

    BigInteger getPendingFluidOutputBig();

    BigInteger getRecipeItemOutputBig(int index);

    BigInteger getRecipeFluidOutputBig(int index);
}
