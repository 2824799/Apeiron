package com.silvia.apeiron.mixin.gregtech.energy;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;

import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTRecipe;

/** The exact batch planner checks the account after native heat and upgrade validation. */
@Mixin(targets = "tectech.thing.metaTileEntity.multi.godforge.MTEBaseModule$GorgeModuleProcessingLogic", remap = false)
public abstract class GodforgeRecipePredictionMixin extends ProcessingLogic {

    @Inject(method = "predictDrainedEnergy", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$prediction(GTRecipe recipe, CallbackInfoReturnable<BigInteger> cir) {
        if (machine instanceof MTEMultiBlockBase && InfiniteEnergyHatches.find((MTEMultiBlockBase) machine) != null)
            cir.setReturnValue(BigInteger.ZERO);
    }
}
