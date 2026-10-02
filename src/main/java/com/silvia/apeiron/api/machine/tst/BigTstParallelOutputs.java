package com.silvia.apeiron.api.machine.tst;

import java.math.BigInteger;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public interface BigTstParallelOutputs {

    void mergeItemOutputBig(ItemStack type, BigInteger amount);

    void mergeFluidOutputBig(FluidStack type, BigInteger amount);
}
