package com.silvia.apeiron.mixin.gregtech.energy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import gregtech.api.logic.ProcessingLogic;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.ParallelHelper;

@Mixin(value = ProcessingLogic.class, remap = false)
public interface ProcessingLogicAccessor {

    @Invoker("createParallelHelper")
    ParallelHelper apeiron$nativeHelper(GTRecipe recipe);

    @Invoker("createOverclockCalculator")
    gregtech.api.util.OverclockCalculator apeiron$nativeCalculator(GTRecipe recipe);

    @Invoker("calculateDuration")
    double apeiron$duration(GTRecipe recipe, ParallelHelper helper, gregtech.api.util.OverclockCalculator calculator);

    @Invoker("onRecipeStart")
    gregtech.api.recipe.check.CheckRecipeResult apeiron$onStart(GTRecipe recipe);

}
