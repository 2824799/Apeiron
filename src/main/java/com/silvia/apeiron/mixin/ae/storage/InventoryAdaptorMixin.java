package com.silvia.apeiron.mixin.ae.storage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigInventoryAdaptors;

import appeng.api.config.InsertionMode;
import appeng.api.storage.data.IAEStack;
import appeng.util.InventoryAdaptor;

/** Gives every AE physical-inventory adapter an exact, chunked stack bridge. */
@Mixin(value = InventoryAdaptor.class, remap = false)
public abstract class InventoryAdaptorMixin {

    @Inject(method = "addStack", at = @At("HEAD"), cancellable = true)
    private void apeiron$addBig(final IAEStack<?> input, final InsertionMode mode,
        final CallbackInfoReturnable<IAEStack<?>> cir) {
        if (BigAEStackValues.get(input)
            .compareTo(java.math.BigInteger.valueOf(Integer.MAX_VALUE - 1L)) > 0) {
            cir.setReturnValue(BigInventoryAdaptors.addStackBig((InventoryAdaptor) (Object) this, input, mode, false));
        }
    }

    @Inject(method = "simulateAddStack", at = @At("HEAD"), cancellable = true)
    private void apeiron$simulateBig(final IAEStack<?> input, final InsertionMode mode,
        final CallbackInfoReturnable<IAEStack<?>> cir) {
        if (BigAEStackValues.get(input)
            .compareTo(java.math.BigInteger.valueOf(Integer.MAX_VALUE - 1L)) > 0) {
            cir.setReturnValue(BigInventoryAdaptors.addStackBig((InventoryAdaptor) (Object) this, input, mode, true));
        }
    }
}
