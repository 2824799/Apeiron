// SPDX-License-Identifier: GPL-3.0-only
// Output chance calculation adapted from Twist Space Technology 0.8.0-RC2.1 (Nxer and contributors).
package com.silvia.apeiron.mixin.tst.output;

import java.math.BigInteger;
import java.util.List;
import java.util.function.Function;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase.FluidStackLong;
import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase.ItemStackLong;
import com.silvia.apeiron.api.machine.tst.BigTstParallelOutputs;
import com.silvia.apeiron.common.machine.tst.output.BigTstOutputLists;

import gregtech.api.enums.GTValues;
import gregtech.api.objects.XSTR;
import gregtech.api.util.GTRecipe;

@Pseudo
@Mixin(
    targets = "com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.processingLogics.GTCM_ParallelHelper",
    remap = false)
public abstract class ParallelOutputBigMixin implements BigTstParallelOutputs {

    @Shadow
    private GTRecipe recipe;
    @Shadow
    private int currentParallel;
    @Shadow
    private ItemStack[] itemOutputs;
    @Shadow
    private FluidStack[] fluidOutputs;
    @Shadow
    private Function<Integer, ItemStack[]> customItemOutputCalculation;
    @Shadow
    private Function<Integer, FluidStack[]> customFluidOutputCalculation;
    @Shadow
    @Final
    private List<ItemStackLong> longItemOutputs;
    @Shadow
    @Final
    private List<FluidStackLong> longFluidOutputs;

    @Inject(method = "calculateLongOutputs", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$calculate(CallbackInfo ci) {
        itemOutputs = GTValues.emptyItemStackArray;
        fluidOutputs = GTValues.emptyFluidStackArray;
        longItemOutputs.clear();
        longFluidOutputs.clear();
        if (customItemOutputCalculation != null) {
            final ItemStack[] outputs = customItemOutputCalculation.apply(currentParallel);
            if (outputs != null) for (ItemStack output : outputs) {
                if (output != null) mergeItemOutputBig(output, BigInteger.valueOf(output.stackSize));
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
                mergeItemOutputBig(
                    type,
                    BigInteger.valueOf(parallels)
                        .multiply(BigInteger.valueOf(type.stackSize)));
            }
        }
        if (customFluidOutputCalculation != null) {
            final FluidStack[] outputs = customFluidOutputCalculation.apply(currentParallel);
            if (outputs != null) for (FluidStack output : outputs) {
                if (output != null) mergeFluidOutputBig(output, BigInteger.valueOf(output.amount));
            }
        } else if (recipe.mFluidOutputs != null) {
            for (FluidStack type : recipe.mFluidOutputs) {
                if (type != null) mergeFluidOutputBig(
                    type,
                    BigInteger.valueOf(type.amount)
                        .multiply(BigInteger.valueOf(currentParallel)));
            }
        }
        ci.cancel();
    }

    @Override
    public void mergeItemOutputBig(ItemStack type, BigInteger amount) {
        BigTstOutputLists.mergeItem(longItemOutputs, type, amount);
    }

    @Override
    public void mergeFluidOutputBig(FluidStack type, BigInteger amount) {
        BigTstOutputLists.mergeFluid(longFluidOutputs, type, amount);
    }
}
