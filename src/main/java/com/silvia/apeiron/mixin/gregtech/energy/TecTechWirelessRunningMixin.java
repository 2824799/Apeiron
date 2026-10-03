package com.silvia.apeiron.mixin.gregtech.energy;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.energy.WirelessControllerEnergy;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import tectech.thing.metaTileEntity.multi.base.TTMultiblockBase;

@Mixin(value = TTMultiblockBase.class, remap = false)
public abstract class TecTechWirelessRunningMixin {

    @Inject(method = "onRunningTick", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$debit(ItemStack slot, CallbackInfoReturnable<Boolean> cir) {
        if (((BigWirelessController) this).getWirelessRecipeState()
            .isRunning()) cir.setReturnValue(WirelessControllerEnergy.debitTick((MTEMultiBlockBase) (Object) this));
    }

    @Inject(method = "addClassicOutputs_EM", at = @At("HEAD"), require = 1)
    private void apeiron$complete(CallbackInfo ci) {
        WirelessControllerEnergy.complete((MTEMultiBlockBase) (Object) this);
    }
}
