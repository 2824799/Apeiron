package com.silvia.apeiron.mixin.gregtech.output;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.common.machine.output.NativeMEOutputCapacity;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTUtility;

/** Beta-1 GT has a fluid ME-capacity method without the later output-list parameter. */
@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class LegacyNativeMEOutputCapacityMixin {

    @Inject(method = "canDumpItemToME(Ljava/util/List;)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$items(List<GTUtility.ItemId> outputs, CallbackInfoReturnable<Boolean> cir) {
        if (NativeMEOutputCapacity.canDumpItems((MTEMultiBlockBase) (Object) this, outputs)) cir.setReturnValue(true);
    }

    @Inject(method = "canDumpFluidToME()Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$fluids(CallbackInfoReturnable<Boolean> cir) {
        if (NativeMEOutputCapacity.hasUnlimitedFluidOutput((MTEMultiBlockBase) (Object) this)) cir.setReturnValue(true);
    }
}
