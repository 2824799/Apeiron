package com.silvia.apeiron.mixin.gregtech.energy;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.energy.WirelessControllerEnergy;

import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** The extended GT base overrides all three hooks, so its debit must be intercepted independently. */
@Mixin(value = MTEExtendedPowerMultiBlockBase.class, remap = false)
public abstract class ExtendedWirelessRunningTickMixin {

    @Inject(method = "setEnergyUsage", at = @At("HEAD"), require = 1)
    private void apeiron$commitExtended(ProcessingLogic logic, CallbackInfo ci) {
        ((BigWirelessController) this).getWirelessRecipeState()
            .commitPreparedRecipe();
    }

    @Inject(method = "onRunningTick", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$debitExtended(ItemStack slot, CallbackInfoReturnable<Boolean> cir) {
        MTEMultiBlockBase controller = (MTEMultiBlockBase) (Object) this;
        com.silvia.apeiron.common.machine.parallel.WirelessRecipeState state = ((BigWirelessController) this)
            .getWirelessRecipeState();
        if (state.isRunning() && !state.usesNativeEnergy())
            cir.setReturnValue(WirelessControllerEnergy.debitTick(controller));
    }

    @Inject(method = "drainEnergyInput", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$legacyExtended(long amount, CallbackInfoReturnable<Boolean> cir) {
        Boolean result = WirelessControllerEnergy.debitLegacy((MTEMultiBlockBase) (Object) this, amount);
        if (result != null) cir.setReturnValue(result);
    }
}
