package com.silvia.apeiron.mixin.gregtech.lanthanides;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.common.machine.me.output.MTEBeamlineMEOutputHatch;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.common.tileentities.machines.multi.beamcrafting.MTEBeamMultiBase.BeamHatchElement;

@Mixin(value = BeamHatchElement.class, remap = false)
public abstract class MEBeamlineOutputElementMixin {

    @Inject(method = "mteClasses", at = @At("RETURN"), cancellable = true, require = 1)
    private void apeiron$beamType(CallbackInfoReturnable<List<? extends Class<? extends IMetaTileEntity>>> cir) {
        if ((Object) this != BeamHatchElement.BeamlineOutput) return;
        List<Class<? extends IMetaTileEntity>> types = new ArrayList<>(cir.getReturnValue());
        types.add(MTEBeamlineMEOutputHatch.class);
        cir.setReturnValue(types);
    }
}
