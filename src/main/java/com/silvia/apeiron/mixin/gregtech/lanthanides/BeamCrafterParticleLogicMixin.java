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
import com.silvia.apeiron.api.machine.parallel.BigRecipeInputProvider;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.integration.lanthanides.BeamCrafterParticleInputs;
import com.silvia.apeiron.common.integration.lanthanides.PatternParticleInputs;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.parallel.BigRecipeParallelHelper;

import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;
import gregtech.common.tileentities.machines.multi.beamcrafting.MTEBeamCrafter;
import gregtech.loaders.postload.recipes.beamcrafter.BeamCrafterMetadata;

/** Keeps the native processing logic, outputs and power checks while adding an item-particle input mode. */
@Pseudo
@Mixin(targets = "gregtech.common.tileentities.machines.multi.beamcrafting.MTEBeamCrafter$1", remap = false)
public abstract class BeamCrafterParticleLogicMixin extends ProcessingLogic implements BigRecipeInputProvider {

    @Shadow
    @Final
    private MTEBeamCrafter this$0;

    @Unique
    private boolean apeiron$itemParticles;
    @Unique
    private boolean apeiron$zeroPowerModifier;

    @Override
    public boolean hasExactRecipeInputs() {
        return apeiron$itemParticles;
    }

    @Override
    public GTRecipe getExactInputRecipe(GTRecipe recipe) {
        return BeamCrafterParticleInputs.inputRecipe(recipe);
    }

    @Override
    protected double calculateDuration(GTRecipe recipe, ParallelHelper helper, OverclockCalculator calculator) {
        // Particle items have already paid the beam requirement. Wireless work ticks own the energy schedule.
        return helper instanceof BigRecipeParallelHelper ? 1 : super.calculateDuration(recipe, helper, calculator);
    }

    @Inject(method = "createOverclockCalculator", at = @At("HEAD"), require = 1)
    private void apeiron$restoreParticlePower(GTRecipe recipe, CallbackInfoReturnable<OverclockCalculator> cir) {
        apeiron$zeroPowerModifier = apeiron$itemParticles && InfiniteEnergyHatches.find(this$0) != null
            && euModifier == 0;
        // Zero is a native parallel-planning sentinel, not a machine energy discount.
        if (apeiron$zeroPowerModifier) euModifier = 1;
    }

    @Inject(method = "createOverclockCalculator", at = @At("RETURN"), require = 1)
    private void apeiron$particleEnergy(GTRecipe recipe, CallbackInfoReturnable<OverclockCalculator> cir) {
        if (apeiron$zeroPowerModifier) euModifier = 0;
        if (apeiron$itemParticles && InfiniteEnergyHatches.find(this$0) != null) cir.getReturnValue()
            .setDuration(1);
    }

    @Inject(method = "createParallelHelper", at = @At("RETURN"), require = 1)
    private void apeiron$particleItems(GTRecipe recipe, CallbackInfoReturnable<ParallelHelper> cir) {
        apeiron$itemParticles = PatternParticleInputs.hasPatternInput(this$0.mDualInputHatches);
        if (InfiniteEnergyHatches.find(this$0) != null) {
            BeamCrafterMetadata metadata = recipe.getMetadata(RecipeMaps.BEAMCRAFTER_METADATA);
            if (metadata != null) {
                long perRecipe = (long) metadata.amount_A + metadata.amount_B;
                int cap = ((BigWirelessController) this$0).getParallelLimitBig()
                    .applyTo(java.math.BigInteger.valueOf(Integer.MAX_VALUE / perRecipe))
                    .intValueExact();
                cir.getReturnValue()
                    .setMaxParallel(cap)
                    .enableBatchMode(1);
            }
        }
        if (apeiron$itemParticles) cir.getReturnValue()
            .setMaxParallelCalculator(BeamCrafterParticleInputs::parallels)
            .setInputConsumer(BeamCrafterParticleInputs::consume);
    }

    @Inject(method = "applyRecipe", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$exactParticles(GTRecipe recipe, ParallelHelper helper, OverclockCalculator calculator,
        CheckRecipeResult result, CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (!(helper instanceof BigRecipeParallelHelper)) return;
        CheckRecipeResult started = super.applyRecipe(recipe, helper, calculator, result);
        if (started.wasSuccessful()) {
            BeamCrafterMetadata metadata = recipe.getMetadata(RecipeMaps.BEAMCRAFTER_METADATA);
            ((BeamItemInputController) this$0).prepareItemParticleRecipe(metadata.particleID_A, metadata.particleID_B);
        }
        cir.setReturnValue(started);
    }

    @Inject(method = "applyRecipe", at = @At("RETURN"), require = 1)
    private void apeiron$paidParticles(GTRecipe recipe, ParallelHelper helper, OverclockCalculator calculator,
        CheckRecipeResult result, CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (cir.getReturnValue()
            .wasSuccessful()) ((BeamItemInputController) this$0).setItemParticleRecipe(apeiron$itemParticles);
    }
}
