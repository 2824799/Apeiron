package com.silvia.apeiron.mixin.gregtech.output;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.output.NativeOutputBatches;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Aggregate native output chunks before exact ME insertion; the native recipe and power mode stay in control. */
@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class NativeOutputBatchMixin {

    @Inject(method = "addItemOutputs", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$items(ItemStack[] outputs, CallbackInfoReturnable<Boolean> cir) {
        MTEMultiBlockBase machine = (MTEMultiBlockBase) (Object) this;
        if (!(machine instanceof BigWirelessController) || !NativeOutputBatches.hasExactItems(machine)) return;
        NativeOutputBatches.completeItems(machine, outputs);
        cir.setReturnValue(true);
    }

}
