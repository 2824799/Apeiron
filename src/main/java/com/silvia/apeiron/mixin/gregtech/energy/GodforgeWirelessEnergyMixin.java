package com.silvia.apeiron.mixin.gregtech.energy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import tectech.thing.metaTileEntity.multi.godforge.MTEBaseModule;

@Mixin(value = MTEBaseModule.class, remap = false)
public abstract class GodforgeWirelessEnergyMixin {

    @Inject(method = "getActualParallel", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$parallel(CallbackInfoReturnable<Integer> cir) {
        MTEMultiBlockBase machine = (MTEMultiBlockBase) (Object) this;
        if (InfiniteEnergyHatches.find(machine) == null) return;
        java.math.BigInteger cap = ((BigWirelessController) this).getWirelessRecipeState()
            .getParallelSettingBig();
        cir.setReturnValue(
            cap.signum() == 0 ? Integer.MAX_VALUE
                : cap.min(java.math.BigInteger.valueOf(Integer.MAX_VALUE))
                    .intValue());
    }

    @Inject(
        method = { "getProcessingVoltage", "getSafeProcessingVoltage", "getMaxInputVoltage" },
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$voltage(CallbackInfoReturnable<Long> cir) {
        MTEMultiBlockBase machine = (MTEMultiBlockBase) (Object) this;
        if (InfiniteEnergyHatches.find(machine) != null)
            cir.setReturnValue(InfiniteEnergyHatches.processingVoltage(machine));
    }
}
