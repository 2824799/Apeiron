package com.silvia.apeiron.mixin.ae.storage;

import java.math.BigInteger;
import java.util.Collections;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigMEInventories;
import com.silvia.apeiron.ae.storage.BigMEInventory;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.MEMonitorHandler;
import appeng.api.storage.data.IAEStack;

/** Exact forwarding and exact listener deltas for AE's standalone monitor wrapper. */
@SuppressWarnings({ "unchecked", "rawtypes" })
@Mixin(value = MEMonitorHandler.class, remap = false)
public abstract class MEMonitorHandlerMixin implements BigMEInventory {

    @Shadow
    protected abstract IMEInventoryHandler getHandler();

    @Shadow
    protected abstract void postChangesToListeners(Iterable<IAEStack<?>> changes, BaseActionSource source);

    @Override
    public IAEStack<?> injectItemsBig(final IAEStack<?> input, final Actionable mode, final BaseActionSource source) {
        if (input == null) return null;
        final IAEStack<?> offered = input.copy();
        final IAEStack<?> result = BigMEInventories.injectItemsBig(this.getHandler(), input, mode, source);
        if (mode == Actionable.MODULATE) {
            final BigInteger delta = BigAEStackValues.get(offered).subtract(BigAEStackValues.get(result));
            if (delta.signum() != 0) this.postChangesToListeners(
                Collections.singletonList(BigAEStackValues.copyWithSize(offered, delta)), source);
        }
        return result;
    }

    @Override
    public IAEStack<?> extractItemsBig(final IAEStack<?> request, final Actionable mode, final BaseActionSource source) {
        if (request == null) return null;
        final IAEStack<?> offered = request.copy();
        final IAEStack<?> result = BigMEInventories.extractItemsBig(this.getHandler(), request, mode, source);
        if (mode == Actionable.MODULATE && result != null) {
            this.postChangesToListeners(Collections.singletonList(
                BigAEStackValues.copyWithSize(offered, BigAEStackValues.get(result).negate())), source);
        }
        return result;
    }

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$inject(final IAEStack<?> input, final Actionable mode, final BaseActionSource source,
        final CallbackInfoReturnable<IAEStack<?>> cir) {
        if (BigAEStackValues.isBig(input)) cir.setReturnValue(this.injectItemsBig(input, mode, source));
    }

    @Inject(method = "extractItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$extract(final IAEStack<?> request, final Actionable mode, final BaseActionSource source,
        final CallbackInfoReturnable<IAEStack<?>> cir) {
        if (BigAEStackValues.isBig(request)) cir.setReturnValue(this.extractItemsBig(request, mode, source));
    }
}
