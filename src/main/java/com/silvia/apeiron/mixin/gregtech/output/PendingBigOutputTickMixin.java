package com.silvia.apeiron.mixin.gregtech.output;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.api.machine.tst.BigTstOutputController;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class PendingBigOutputTickMixin {

    @Inject(method = "onPostTick", at = @At("RETURN"), require = 1)
    private void apeiron$retry(IGregTechTileEntity tile, long tick, CallbackInfo ci) {
        if (tile.isServerSide() && tick % 20 == 0 && this instanceof BigTstOutputController)
            ((BigTstOutputController) this).flushOutputsBig();
    }
}
