package com.silvia.apeiron.mixin.gregtech.output;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.common.machine.output.MixedOutputPorts;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.GTPPMultiBlockBase;

/** GT++'s custom type dispatcher must keep both output channels of a mixed assembly. */
@Mixin(value = GTPPMultiBlockBase.class, remap = false)
public abstract class GtppMixedOutputRegistrationMixin {

    @Inject(
        method = "addToMachineList(Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;I)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$tile(IGregTechTileEntity tile, int texture, CallbackInfoReturnable<Boolean> cir) {
        if (tile != null
            && MixedOutputPorts.register((MTEMultiBlockBase) (Object) this, tile.getMetaTileEntity(), texture))
            cir.setReturnValue(true);
    }

    @Inject(
        method = "addToMachineList(Lgregtech/api/interfaces/metatileentity/IMetaTileEntity;I)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$meta(IMetaTileEntity meta, int texture, CallbackInfoReturnable<Boolean> cir) {
        if (MixedOutputPorts.register((MTEMultiBlockBase) (Object) this, meta, texture)) cir.setReturnValue(true);
    }
}
