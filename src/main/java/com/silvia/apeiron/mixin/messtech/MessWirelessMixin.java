package com.silvia.apeiron.mixin.messtech;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.integration.messtech.MessRecipeController;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;

@Pseudo
@Mixin(targets = "com.MessTech.common.machine.Base.MTWirelessMultiMachineBase", remap = false)
public abstract class MessWirelessMixin
    implements com.silvia.apeiron.common.integration.messtech.MessWirelessController {

    @Inject(method = "checkProcessingWirelessLoop", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$begin(CallbackInfoReturnable<CheckRecipeResult> cir) {
        MTEMultiBlockBase machine = (MTEMultiBlockBase) (Object) this;
        if (InfiniteEnergyHatches.find(machine) == null) return;
        if (((BigWirelessController) this).getWirelessRecipeState()
            .isRunning()) cir.setReturnValue(CheckRecipeResultRegistry.NO_RECIPE);
        else((MessRecipeController) this).getMessRecipeBatch()
            .begin();
    }

    @Redirect(
        method = "checkProcessingWirelessLoop",
        at = @At(
            value = "INVOKE",
            target = "Lcom/MessTech/common/machine/Base/MTWirelessMultiMachineBase;wirelessModeProcessOnce()Lgregtech/api/recipe/check/CheckRecipeResult;"),
        require = 1)
    private CheckRecipeResult apeiron$step(@Coerce Object machine) {
        return ((MessRecipeController) machine).getMessRecipeBatch()
            .step((MTEMultiBlockBase) machine);
    }

    @Inject(method = "checkProcessingWirelessLoop", at = @At("RETURN"), require = 1)
    private void apeiron$finish(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (InfiniteEnergyHatches.find((MTEMultiBlockBase) (Object) this) != null)
            ((MessRecipeController) this).getMessRecipeBatch()
                .finish((MTEMultiBlockBase) (Object) this);
    }

    @Inject(method = "startWirelessRecipe", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$startup(GTRecipe recipe, int parallel, long eut, int duration, FluidStack[] fluids,
        ItemStack[] items, CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (com.silvia.apeiron.common.machine.parallel.ExactProcessingContext
            .isStarting((MTEMultiBlockBase) (Object) this)) cir.setReturnValue(CheckRecipeResultRegistry.SUCCESSFUL);
    }
}
