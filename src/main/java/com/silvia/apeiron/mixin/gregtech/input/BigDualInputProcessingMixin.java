package com.silvia.apeiron.mixin.gregtech.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.api.machine.me.input.BigDualInputHatch;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.stocking.StockingInputHost;
import com.silvia.apeiron.common.machine.me.stocking.StockingInputLogic;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.tileentities.machines.IDualInputHatch;

/** Source plus any number of mirrors in one controller produce one accounting session. */
@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class BigDualInputProcessingMixin {

    @Inject(method = "startRecipeProcessing", at = @At("HEAD"), require = 1)
    private void apeiron$beginBigInputs(CallbackInfo ci) {
        for (MTEInfinitePatternInputAssembly source : apeiron$sources()) source.beginRecipeProcessing();
        for (IDualInputHatch hatch : ((MTEMultiBlockBase) (Object) this).mDualInputHatches)
            if (hatch instanceof StockingInputHost) ((StockingInputHost) hatch).getStockingInput()
                .begin();
    }

    @Inject(method = "endRecipeProcessing", at = @At("HEAD"), require = 1)
    private void apeiron$commitStockingInputs(CallbackInfo ci) {
        MTEMultiBlockBase controller = (MTEMultiBlockBase) (Object) this;
        Set<StockingInputLogic> stocks = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Object> hatches = new ArrayList<>();
        hatches.addAll(controller.mInputBusses);
        hatches.addAll(controller.mInputHatches);
        hatches.addAll(controller.mDualInputHatches);
        for (Object hatch : hatches)
            if (hatch instanceof StockingInputHost) stocks.add(((StockingInputHost) hatch).getStockingInput());
        controller.setResultIfFailure(StockingInputLogic.finishGroup(stocks));
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
