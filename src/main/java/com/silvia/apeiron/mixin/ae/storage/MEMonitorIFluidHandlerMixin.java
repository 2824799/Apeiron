package com.silvia.apeiron.mixin.ae.storage;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigInventoryAdaptors;
import com.silvia.apeiron.ae.storage.BigMEInventory;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import appeng.me.storage.MEMonitorIFluidHandler;

/** Physical tank operations remain int-sized; every untransferred AE amount stays exact. */
@Mixin(value = MEMonitorIFluidHandler.class, remap = false)
public abstract class MEMonitorIFluidHandlerMixin implements BigMEInventory {

    @Override
    public IAEStack<?> injectItemsBig(final IAEStack<?> input, final Actionable mode, final BaseActionSource source) {
        if (!(input instanceof IAEFluidStack)) return input;
        final BigInteger requested = BigAEStackValues.get(input);
        if (requested.signum() <= 0) return input;
        BigInteger remaining = requested;
        do {
            final BigInteger amount = remaining.min(BigInventoryAdaptors.MAX_EXTERNAL_CHUNK);
            final IAEFluidStack chunk = BigAEStackValues.copyWithSize((IAEFluidStack) input, amount);
            final IAEFluidStack rejected = ((MEMonitorIFluidHandler) (Object) this).injectItems(chunk, mode, source);
            final BigInteger left = BigAEStackValues.get(rejected);
            BigInventoryAdaptors.checkReturnedAmount(left, amount);
            final BigInteger accepted = amount.subtract(left);
            remaining = remaining.subtract(accepted);
            // A simulated external tank cannot reserve capacity between calls.
            if (mode == Actionable.SIMULATE || accepted.signum() == 0 || left.signum() > 0) break;
        } while (remaining.signum() > 0);
        return remaining.signum() == 0 ? null : BigAEStackValues.copyWithSize(input, remaining);
    }

    @Override
    public IAEStack<?> extractItemsBig(final IAEStack<?> request, final Actionable mode, final BaseActionSource source) {
        if (!(request instanceof IAEFluidStack) || BigAEStackValues.get(request).signum() <= 0) return null;
        final BigInteger requested = BigAEStackValues.get(request);
        BigInteger extracted = BigInteger.ZERO;
        do {
            final BigInteger amount = requested.subtract(extracted).min(BigInventoryAdaptors.MAX_EXTERNAL_CHUNK);
            final IAEFluidStack chunk = BigAEStackValues.copyWithSize((IAEFluidStack) request, amount);
            final IAEFluidStack result = ((MEMonitorIFluidHandler) (Object) this).extractItems(chunk, mode, source);
            if (result == null) break;
            final BigInteger actual = BigAEStackValues.get(result);
            BigInventoryAdaptors.checkReturnedAmount(actual, amount);
            extracted = extracted.add(actual);
            if (mode == Actionable.SIMULATE || actual.signum() == 0 || actual.compareTo(amount) < 0) break;
        } while (extracted.compareTo(requested) < 0);
        return extracted.signum() == 0 ? null : BigAEStackValues.copyWithSize(request, extracted);
    }

    @Inject(method = "injectItems(Lappeng/api/storage/data/IAEFluidStack;Lappeng/api/config/Actionable;Lappeng/api/networking/security/BaseActionSource;)Lappeng/api/storage/data/IAEFluidStack;",
        at = @At("HEAD"), cancellable = true)
    private void apeiron$inject(final IAEFluidStack input, final Actionable mode, final BaseActionSource source,
        final CallbackInfoReturnable<IAEFluidStack> cir) {
        if (BigAEStackValues.get(input).compareTo(BigInventoryAdaptors.MAX_EXTERNAL_CHUNK) > 0) {
            cir.setReturnValue((IAEFluidStack) this.injectItemsBig(input, mode, source));
        }
    }

    @Inject(method = "extractItems(Lappeng/api/storage/data/IAEFluidStack;Lappeng/api/config/Actionable;Lappeng/api/networking/security/BaseActionSource;)Lappeng/api/storage/data/IAEFluidStack;",
        at = @At("HEAD"), cancellable = true)
    private void apeiron$extract(final IAEFluidStack request, final Actionable mode, final BaseActionSource source,
        final CallbackInfoReturnable<IAEFluidStack> cir) {
        if (BigAEStackValues.get(request).compareTo(BigInventoryAdaptors.MAX_EXTERNAL_CHUNK) > 0) {
            cir.setReturnValue((IAEFluidStack) this.extractItemsBig(request, mode, source));
        }
    }
}
