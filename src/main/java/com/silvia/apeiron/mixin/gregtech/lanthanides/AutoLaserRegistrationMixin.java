package com.silvia.apeiron.mixin.gregtech.lanthanides;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.common.machine.lanthanides.MTEAutoLaserBeamlineInput;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.tileentities.machines.multi.beamcrafting.MTEBeamMultiBase;
import gtnhlanth.common.tileentity.MTETargetChamber;

/** The native input class already accepts subclasses; restrict this source to target chambers. */
@Mixin(value = MTEBeamMultiBase.class, remap = false)
public abstract class AutoLaserRegistrationMixin {

    @Inject(method = "addBeamLineInputHatch", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$targetOnly(IGregTechTileEntity tile, int texture, CallbackInfoReturnable<Boolean> cir) {
        if (tile == null || !(tile.getMetaTileEntity() instanceof MTEAutoLaserBeamlineInput)) return;
        if (!((Object) this instanceof MTETargetChamber)) {
            cir.setReturnValue(false);
            return;
        }
        MTEBeamMultiBase<?> machine = (MTEBeamMultiBase<?>) (Object) this;
        MTEAutoLaserBeamlineInput hatch = (MTEAutoLaserBeamlineInput) tile.getMetaTileEntity();
        hatch.updateTexture(texture);
        if (machine.mInputBeamline.contains(hatch)) cir.setReturnValue(true);
    }
}
