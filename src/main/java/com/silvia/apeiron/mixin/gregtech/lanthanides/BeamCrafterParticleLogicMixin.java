package com.silvia.apeiron.mixin.gregtech.lanthanides;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.api.machine.lanthanides.BeamItemInputController;
import com.silvia.apeiron.common.integration.lanthanides.BeamCrafterParticleInputs;

import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;
import gregtech.common.tileentities.machines.multi.beamcrafting.MTEBeamCrafter;

/** Keeps the native processing logic, outputs and power checks while adding an item-particle input mode. */
@Pseudo
@Mixin(targets = "gregtech.common.tileentities.machines.multi.beamcrafting.MTEBeamCrafter$1", remap = false)
public abstract class BeamCrafterParticleLogicMixin extends ProcessingLogic {

    @Shadow
    @Final
    private MTEBeamCrafter this$0;

    @Unique
    private boolean apeiron$itemParticles;

    @Inject(method = "createParallelHelper", at = @At("RETURN"), require = 1)
    private void apeiron$particleItems(GTRecipe recipe, CallbackInfoReturnable<ParallelHelper> cir) {
        apeiron$itemParticles = BeamCrafterParticleInputs.hasParticles(inputItems);
        if (apeiron$itemParticles) cir.getReturnValue()
            .setMaxParallelCalculator(BeamCrafterParticleInputs::parallels)
            .setInputConsumer(BeamCrafterParticleInputs::consume);
    }

    @Inject(method = "applyRecipe", at = @At("RETURN"), require = 1)
    private void apeiron$paidParticles(GTRecipe recipe, ParallelHelper helper, OverclockCalculator calculator,
        CheckRecipeResult result, CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (cir.getReturnValue()
            .wasSuccessful()) ((BeamItemInputController) this$0).setItemParticleRecipe(apeiron$itemParticles);
    }
}
