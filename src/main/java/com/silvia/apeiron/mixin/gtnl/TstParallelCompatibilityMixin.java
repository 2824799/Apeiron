package com.silvia.apeiron.mixin.gtnl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.science.gtnl.config.MainConfig;
import com.science.gtnl.utils.enums.ModList;
import com.science.gtnl.utils.recipes.GTNLParallelHelper;
import com.silvia.apeiron.common.machine.parallel.RecipeChanceEffects;

import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.ParallelHelper;

/** Bind GTNL's existing chance and result hooks to the state inherited by recent TST versions. */
@Pseudo
@Mixin(
    targets = "com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.processingLogics.GTCM_ParallelHelper",
    remap = false)
public abstract class TstParallelCompatibilityMixin extends ParallelHelper {

    @Inject(method = "determineParallel", at = @At("HEAD"), require = 1)
    private void apeiron$chanceBonus(CallbackInfo ci) {
        recipe = RecipeChanceEffects.apply(machine, recipe, chanceMultiplier);
    }

    @Inject(method = "determineParallel", at = @At("RETURN"), require = 1)
    private void apeiron$parallelResult(CallbackInfo ci) {
        if (MainConfig.machine.enableRecipeOutputChance && !ModList.Overpowered.isModLoaded()
            && result == CheckRecipeResultRegistry.INTERNAL_ERROR)
            result = currentParallel == 0 ? GTNLParallelHelper.PARALLEL_ZERO : GTNLParallelHelper.PARALLEL_OVERFLOW;
    }
}
