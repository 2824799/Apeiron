package com.silvia.apeiron.mixin.gregtech.energy;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.energy.WirelessControllerEnergy;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;

import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class WirelessRunningTickMixin {

    @Inject(method = "setEnergyUsage", at = @At("HEAD"), require = 1)
    private void apeiron$commit(ProcessingLogic logic, CallbackInfo ci) {
        ((BigWirelessController) this).getWirelessRecipeState()
            .commitPreparedRecipe();
    }

    @Inject(method = "onRunningTick", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$debit(ItemStack slot, CallbackInfoReturnable<Boolean> cir) {
        WirelessRecipeState state = ((BigWirelessController) this).getWirelessRecipeState();
        if (!state.isRunning() || state.usesNativeEnergy()) return;
        MTEMultiBlockBase machine = (MTEMultiBlockBase) (Object) this;
        cir.setReturnValue(WirelessControllerEnergy.debitTick(machine));
    }

    @Inject(method = "drainEnergyInput", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$nativeDebit(long amount, CallbackInfoReturnable<Boolean> cir) {
        MTEMultiBlockBase machine = (MTEMultiBlockBase) (Object) this;
        Boolean result = WirelessControllerEnergy.debitLegacy(machine, amount);
        if (result != null) cir.setReturnValue(result);
    }
}
