package com.silvia.apeiron.mixin.gregtech.lanthanides;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.common.machine.me.output.MTEBeamlineMEOutputHatch;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.tileentities.machines.multi.beamcrafting.MTEBeamMultiBase;
import gregtech.common.tileentities.machines.multi.beamcrafting.MTEBeamMultiBase.FundamentalForce;

/** Register the beam view through the original adder so advanced output particle selection stays native. */
@Mixin(value = MTEBeamMultiBase.class, remap = false)
public abstract class MEBeamlineOutputRegistrationMixin {

    @Inject(method = "addBeamLineOutputHatch", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$beamOutput(IGregTechTileEntity tile, int texture, CallbackInfoReturnable<Boolean> cir) {
        if (tile == null || !(tile.getMetaTileEntity() instanceof MTEBeamlineMEOutputHatch)) return;
        MTEBeamlineMEOutputHatch hatch = (MTEBeamlineMEOutputHatch) tile.getMetaTileEntity();
        MTEBeamMultiBase<?> machine = (MTEBeamMultiBase<?>) (Object) this;
        hatch.updateTexture(texture);
        hatch.getBeamOutput()
            .updateTexture(texture);
        cir.setReturnValue(
            machine.mOutputBeamline.contains(hatch.getBeamOutput()) || machine.addBeamLineOutputHatch(
                hatch.getBeamOutput()
                    .getBaseMetaTileEntity(),
                texture));
    }

    @Inject(method = "addAdvancedBeamlineOutputHatch", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$advancedOutput(IGregTechTileEntity tile, int texture, FundamentalForce force,
        CallbackInfoReturnable<Boolean> cir) {
        if (tile == null || !(tile.getMetaTileEntity() instanceof MTEBeamlineMEOutputHatch)) return;
        MTEBeamlineMEOutputHatch hatch = (MTEBeamlineMEOutputHatch) tile.getMetaTileEntity();
        MTEBeamMultiBase<?> machine = (MTEBeamMultiBase<?>) (Object) this;
        hatch.updateTexture(texture);
        cir.setReturnValue(
            machine.mAdvancedOutputBeamline.contains(hatch.getBeamOutput()) || machine.addAdvancedBeamlineOutputHatch(
                hatch.getBeamOutput()
                    .getBaseMetaTileEntity(),
                texture,
                force));
    }
}
