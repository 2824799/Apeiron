package com.silvia.apeiron.mixin.messtech;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.common.integration.messtech.MessMiningRecipes;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;

@Pseudo
@Mixin(targets = "com.MessTech.common.machine.module.SpaceModuleMinerInfinity", remap = false)
public abstract class MessMinerMixin {

    @Inject(
        method = "checkProcessing_EM",
        at = @At(
            value = "INVOKE",
            target = "Lcom/MessTech/common/machine/module/SpaceModuleMinerInfinity;startBatchedProcessing()Lgregtech/api/recipe/check/CheckRecipeResult;"),
        cancellable = true,
        require = 1)
    private void apeiron$mine(CallbackInfoReturnable<CheckRecipeResult> cir) {
        CheckRecipeResult result = MessMiningRecipes.process((MTEMultiBlockBase) (Object) this);
        if (result != null) cir.setReturnValue(result);
    }
}
