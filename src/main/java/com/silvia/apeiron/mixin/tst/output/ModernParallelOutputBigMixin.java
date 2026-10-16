// SPDX-License-Identifier: GPL-3.0-only
// Output chance calculation adapted from Twist Space Technology 0.8.0-RC2.1 (Nxer and contributors).
package com.silvia.apeiron.mixin.tst.output;

import java.math.BigInteger;
import java.util.List;

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
import com.silvia.apeiron.common.machine.tst.output.BigTstParallelOutputCalculation;

import gregtech.api.enums.GTValues;
import gregtech.api.util.ParallelHelper;

@Pseudo
@Mixin(
    targets = "com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.processingLogics.GTCM_ParallelHelper",
    remap = false)
public abstract class ModernParallelOutputBigMixin extends ParallelHelper implements BigTstParallelOutputs {

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
        BigTstParallelOutputCalculation
            .calculate(recipe, currentParallel, customItemOutputCalculation, customFluidOutputCalculation, this);
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
