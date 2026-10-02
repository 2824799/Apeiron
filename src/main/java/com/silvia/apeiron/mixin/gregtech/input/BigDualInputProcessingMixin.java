package com.silvia.apeiron.mixin.gregtech.input;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.api.machine.me.input.BigDualInputHatch;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.tileentities.machines.IDualInputHatch;

/** Source plus any number of mirrors in one controller produce one accounting session. */
@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class BigDualInputProcessingMixin {

    @Inject(method = "startRecipeProcessing", at = @At("HEAD"), require = 1)
    private void apeiron$beginBigInputs(CallbackInfo ci) {
        for (MTEInfinitePatternInputAssembly source : apeiron$sources()) source.beginRecipeProcessing();
    }

    @Inject(method = "endRecipeProcessing", at = @At("RETURN"), require = 1)
    private void apeiron$endBigInputs(CallbackInfo ci) {
        for (MTEInfinitePatternInputAssembly source : apeiron$sources()) source.endRecipeProcessing();
    }

    @Unique
    private Set<MTEInfinitePatternInputAssembly> apeiron$sources() {
        Set<MTEInfinitePatternInputAssembly> result = Collections.newSetFromMap(new IdentityHashMap<>());
        for (IDualInputHatch hatch : ((MTEMultiBlockBase) (Object) this).mDualInputHatches)
            if (hatch instanceof BigDualInputHatch) {
                MTEInfinitePatternInputAssembly source = ((BigDualInputHatch) hatch).getInputSource();
                if (source != null) result.add(source);
            }
        return result;
    }
}
