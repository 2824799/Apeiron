package com.silvia.apeiron.mixin.gregtech.energy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.silvia.apeiron.common.machine.parallel.GeneratedRecipes;
import com.silvia.apeiron.common.machine.parallel.ItemProcessingRecipes;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;

/** Dispatch at the shared caller so custom checkProcessing overrides cannot bypass the capability. */
@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class ItemProcessingDispatchMixin {

    @Redirect(
        method = "checkRecipe()Z",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/metatileentity/implementations/MTEMultiBlockBase;checkProcessing()Lgregtech/api/recipe/check/CheckRecipeResult;"),
        require = 1)
    private CheckRecipeResult apeiron$processItemSource(MTEMultiBlockBase machine) {
        CheckRecipeResult result = ItemProcessingRecipes.process(machine);
        if (result == null) result = GeneratedRecipes.process(machine);
        return result == null ? machine.checkProcessing() : result;
    }
}
