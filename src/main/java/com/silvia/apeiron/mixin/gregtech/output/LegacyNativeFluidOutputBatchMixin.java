package com.silvia.apeiron.mixin.gregtech.output;

import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.output.NativeOutputBatches;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class LegacyNativeFluidOutputBatchMixin {

    @Inject(
        method = "addFluidOutputs([Lnet/minecraftforge/fluids/FluidStack;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$fluids(FluidStack[] outputs, CallbackInfo ci) {
        MTEMultiBlockBase machine = (MTEMultiBlockBase) (Object) this;
        if (!(machine instanceof BigWirelessController) || !NativeOutputBatches.hasExactFluids(machine)) return;
        NativeOutputBatches.completeFluids(machine, outputs);
        ci.cancel();
    }
}
