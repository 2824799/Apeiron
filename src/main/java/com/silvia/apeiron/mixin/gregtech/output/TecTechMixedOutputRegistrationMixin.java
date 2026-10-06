package com.silvia.apeiron.mixin.gregtech.output;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.common.machine.output.MixedOutputPorts;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import tectech.thing.metaTileEntity.multi.base.TTMultiblockBase;

/** Registers the hybrid's hidden fluid view in TecTech's separate hatch scanners. */
@Mixin(value = TTMultiblockBase.class, remap = false)
public abstract class TecTechMixedOutputRegistrationMixin {

    @Inject(method = "addToMachineList", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$registerGeneric(IGregTechTileEntity tile, int texture, CallbackInfoReturnable<Boolean> cir) {
        if (apeiron$register(tile, texture)) cir.setReturnValue(true);
    }

    @Inject(method = "addClassicToMachineList", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$registerClassic(IGregTechTileEntity tile, int texture, CallbackInfoReturnable<Boolean> cir) {
        if (apeiron$register(tile, texture)) cir.setReturnValue(true);
    }

    @Inject(method = "addOutputToMachineList", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$registerOutput(IGregTechTileEntity tile, int texture, CallbackInfoReturnable<Boolean> cir) {
        if (apeiron$register(tile, texture)) cir.setReturnValue(true);
    }

    @Unique
    private boolean apeiron$register(IGregTechTileEntity tile, int texture) {
        if (tile == null) return false;
        return MixedOutputPorts.register((TTMultiblockBase) (Object) this, tile.getMetaTileEntity(), texture);
    }
}
