package com.silvia.apeiron.mixin.gregtech.output;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** A hybrid discovered as either output kind must appear in both native controller lists exactly once. */
@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class MixedOutputRegistrationMixin {

    @Inject(method = "addOutputBusToMachineList", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$deduplicate(IGregTechTileEntity tile, int texture, CallbackInfoReturnable<Boolean> cir) {
        if (tile == null || !(tile.getMetaTileEntity() instanceof MTEInfiniteMEOutputAssembly)) return;
        final MTEInfiniteMEOutputAssembly assembly = (MTEInfiniteMEOutputAssembly) tile.getMetaTileEntity();
        if (((MTEMultiBlockBase) (Object) this).mOutputBusses.contains(assembly)) {
            apeiron$register(assembly, texture);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "addOutputBusToMachineList", at = @At("RETURN"), require = 1)
    private void apeiron$registerFluidView(IGregTechTileEntity tile, int texture, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || tile == null || !(tile.getMetaTileEntity() instanceof MTEInfiniteMEOutputAssembly))
            return;
        apeiron$register((MTEInfiniteMEOutputAssembly) tile.getMetaTileEntity(), texture);
    }

    @Inject(method = "addOutputHatchToMachineList", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$registerHybrid(IGregTechTileEntity tile, int texture, CallbackInfoReturnable<Boolean> cir) {
        if (tile == null || !(tile.getMetaTileEntity() instanceof MTEInfiniteMEOutputAssembly)) return;
        ((MTEMultiBlockBase) (Object) this).addOutputBusToMachineList(tile, texture);
        cir.setReturnValue(true);
    }

    @Unique
    private void apeiron$register(MTEInfiniteMEOutputAssembly assembly, int texture) {
        final MTEMultiBlockBase machine = (MTEMultiBlockBase) (Object) this;
        assembly.updateTexture(texture);
        if (!machine.mOutputBusses.contains(assembly)) machine.mOutputBusses.add(assembly);
        if (!machine.mOutputHatches.contains(assembly.getFluidOutput()))
            machine.mOutputHatches.add(assembly.getFluidOutput());
    }
}
