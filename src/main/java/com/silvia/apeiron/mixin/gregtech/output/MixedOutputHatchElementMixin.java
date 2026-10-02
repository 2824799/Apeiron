package com.silvia.apeiron.mixin.gregtech.output;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;

import gregtech.api.enums.HatchElement;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

@Mixin(value = HatchElement.class, remap = false)
public abstract class MixedOutputHatchElementMixin {

    @Inject(method = "mteClasses", at = @At("RETURN"), cancellable = true, require = 1)
    private void apeiron$allowAssembly(CallbackInfoReturnable<List<? extends Class<? extends IMetaTileEntity>>> cir) {
        if ((Object) this != HatchElement.OutputHatch) return;
        final List<Class<? extends IMetaTileEntity>> classes = new ArrayList<>(cir.getReturnValue());
        classes.add(MTEInfiniteMEOutputAssembly.class);
        cir.setReturnValue(classes);
    }
}
