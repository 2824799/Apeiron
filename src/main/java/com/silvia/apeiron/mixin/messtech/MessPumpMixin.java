package com.silvia.apeiron.mixin.messtech;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.common.integration.messtech.MessModuleRecipes;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;

@Pseudo
@Mixin(targets = "com.MessTech.common.machine.module.SpaceModulePumpInfinity", remap = false)
public abstract class MessPumpMixin {

    @Inject(
        method = "checkProcessing_EM",
        at = @At(
            value = "INVOKE",
            target = "Lcom/MessTech/common/machine/module/SpaceModulePumpInfinity;startBatchedProcessing()Lgregtech/api/recipe/check/CheckRecipeResult;"),
        cancellable = true,
        require = 1)
    private void apeiron$process(CallbackInfoReturnable<CheckRecipeResult> cir) {
        CheckRecipeResult result = MessModuleRecipes.pump((MTEMultiBlockBase) (Object) this);
        if (result != null) cir.setReturnValue(result);
    }
}
