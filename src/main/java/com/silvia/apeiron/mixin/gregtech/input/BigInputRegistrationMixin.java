package com.silvia.apeiron.mixin.gregtech.input;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.api.machine.me.input.BigDualInputHatch;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class BigInputRegistrationMixin {

    @Inject(
        method = { "addToMachineList", "addInputBusToMachineList", "addInputHatchToMachineList" },
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$deduplicateBigInput(IGregTechTileEntity tile, int texture,
        CallbackInfoReturnable<Boolean> cir) {
        if (tile == null || !(tile.getMetaTileEntity() instanceof BigDualInputHatch)) return;
        BigDualInputHatch hatch = (BigDualInputHatch) tile.getMetaTileEntity();
        MTEMultiBlockBase controller = (MTEMultiBlockBase) (Object) this;
        if (controller.mDualInputHatches.contains(hatch)) {
            hatch.updateTexture(texture);
            hatch.updateCraftingIcon(controller.getMachineCraftingIcon());
            cir.setReturnValue(true);
        }
    }
}
