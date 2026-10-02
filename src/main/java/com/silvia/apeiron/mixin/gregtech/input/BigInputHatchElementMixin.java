package com.silvia.apeiron.mixin.gregtech.input;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputMirror;

import gregtech.api.enums.HatchElement;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/** A combined input and its mirror can occupy the standard fluid-input structure positions. */
@Mixin(targets = "gregtech.api.enums.HatchElement$3", remap = false)
public abstract class BigInputHatchElementMixin {

    @Inject(method = "mteClasses", at = @At("RETURN"), cancellable = true, require = 1)
    private void apeiron$allowBigInput(CallbackInfoReturnable<List<? extends Class<? extends IMetaTileEntity>>> cir) {
        if ((Object) this != HatchElement.InputHatch) return;
        List<Class<? extends IMetaTileEntity>> types = new ArrayList<>(cir.getReturnValue());
        types.add(MTEInfinitePatternInputAssembly.class);
        types.add(MTEInfinitePatternInputMirror.class);
        cir.setReturnValue(types);
    }
}
