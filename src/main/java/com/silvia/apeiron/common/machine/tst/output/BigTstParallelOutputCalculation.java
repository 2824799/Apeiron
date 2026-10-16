// SPDX-License-Identifier: GPL-3.0-only
// Output chance calculation adapted from Twist Space Technology 0.8.0-RC2.1 (Nxer and contributors).
package com.silvia.apeiron.common.machine.tst.output;

import java.math.BigInteger;
import java.util.function.Function;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.api.machine.tst.BigTstParallelOutputs;

import gregtech.api.objects.XSTR;
import gregtech.api.util.GTRecipe;

/** The same exact calculation for TST's declared fields and its newer inherited GT fields. */
public final class BigTstParallelOutputCalculation {

    private BigTstParallelOutputCalculation() {}

    public static void calculate(GTRecipe recipe, int currentParallel,
        Function<Integer, ItemStack[]> customItemOutputCalculation,
        Function<Integer, FluidStack[]> customFluidOutputCalculation, BigTstParallelOutputs sink) {
        if (customItemOutputCalculation != null) {
            final ItemStack[] outputs = customItemOutputCalculation.apply(currentParallel);
            if (outputs != null) for (ItemStack output : outputs) {
                if (output != null) sink.mergeItemOutputBig(output, BigInteger.valueOf(output.stackSize));
            }
        } else if (recipe.mOutputs != null) {
            for (int i = 0; i < recipe.mOutputs.length; i++) {
                final ItemStack type = recipe.getOutput(i);
                if (type == null) continue;
                final int chance = recipe.getOutputChance(i);
                long parallels = currentParallel;
                if (chance < 10000) {
                    parallels = (long) currentParallel * chance / 10000;
                    final long remainder = (long) currentParallel * chance % 10000;
                    if (remainder > 0 && remainder > XSTR.XSTR_INSTANCE.nextInt(10000)) parallels++;
                }
                sink.mergeItemOutputBig(
                    type,
                    BigInteger.valueOf(parallels)
                        .multiply(BigInteger.valueOf(type.stackSize)));
            }
        }
        if (customFluidOutputCalculation != null) {
            final FluidStack[] outputs = customFluidOutputCalculation.apply(currentParallel);
            if (outputs != null) for (FluidStack output : outputs) {
                if (output != null) sink.mergeFluidOutputBig(output, BigInteger.valueOf(output.amount));
            }
        } else if (recipe.mFluidOutputs != null) {
            for (FluidStack type : recipe.mFluidOutputs) {
                if (type != null) sink.mergeFluidOutputBig(
                    type,
                    BigInteger.valueOf(type.amount)
                        .multiply(BigInteger.valueOf(currentParallel)));
            }
        }
    }
}
