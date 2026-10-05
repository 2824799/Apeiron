package com.silvia.apeiron.mixin.gregtech.quantum;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.api.machine.parallel.BigRecipeOutputProvider;
import com.silvia.apeiron.api.machine.quantum.EnhancedQuantumController;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.quantum.QuantumEnhancementRecipes;

import appeng.api.storage.data.IAEStack;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.util.GTRecipe;

/** Opt into shared exact energy and input handling after preserving the QFT-specific recipe checks. */
@Mixin(
    targets = "gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.production.MTEQuantumForceTransformer$3",
    remap = false)
public abstract class QuantumProcessingEnhancementMixin extends ProcessingLogic implements BigRecipeOutputProvider {

    @Shadow
    private int[] chances;
    @Shadow
    private FluidStack[] fluidModeItems;

    @Override
    public boolean hasExactRecipeOutputs() {
        return machine instanceof EnhancedQuantumController
            && ((EnhancedQuantumController) machine).hasQuantumEnhancement();
    }

    @Override
    public List<IAEStack<?>> calculateRecipeOutputsBig(GTRecipe recipe, BigInteger parallels) {
        return QuantumEnhancementRecipes.outputs(recipe, fluidModeItems, parallels);
    }

    @Inject(method = "validateRecipe", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$validateEnhancedRecipe(GTRecipe recipe, CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (!hasExactRecipeOutputs()) return;
        EnhancedQuantumController quantum = (EnhancedQuantumController) machine;
        if (recipe.mSpecialValue > quantum.getQuantumCraftingTier()) {
            cir.setReturnValue(CheckRecipeResultRegistry.insufficientMachineTier(recipe.mSpecialValue));
            return;
        }
        MTEMultiBlockBase controller = (MTEMultiBlockBase) machine;
        if (!QuantumEnhancementRecipes.hasCatalyst(controller, recipe, inputItems, inputFluids)) {
            cir.setReturnValue(SimpleCheckRecipeResult.ofFailure("no_catalyst"));
            return;
        }
        maxParallel = InfiniteEnergyHatches.find(controller) == null ? controller.getTrueParallel() : Integer.MAX_VALUE;
        quantum.prepareEnhancedQuantumRecipe(maxParallel);
        chances = new int[recipe.mOutputs.length + recipe.mFluidOutputs.length];
        Arrays.fill(chances, 10000);
        fluidModeItems = QuantumEnhancementRecipes.fluidModeItems(recipe, quantum.isQuantumFluidMode());
        cir.setReturnValue(CheckRecipeResultRegistry.SUCCESSFUL);
    }
}
