package com.silvia.apeiron.mixin.aeinfinitycell.ae;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigUnlimitedMEInventory;
import com.silvia.apeiron.api.aeinfinitycell.BigInfinityCellRecord;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import cn.dancingsnow.aeinfinitycell.storage.InfinityCellRecord;

/** Keeps legacy signatures while exposing exact transfers for every supported channel. */
@Pseudo
@Mixin(targets = "cn.dancingsnow.aeinfinitycell.ae.AbstractInfinityInventoryHandler", remap = false)
public abstract class InfinityInventoryBigMixin implements BigUnlimitedMEInventory {

    @Shadow
    protected abstract InfinityCellRecord record();

    @Shadow
    protected abstract void markChanged();

    @Shadow
    public abstract IAEStackType<?> getStackType();

    @Override
    public IAEStack<?> injectItemsBig(final IAEStack<?> input, final Actionable mode, final BaseActionSource source) {
        final BigInteger amount = BigAEStackValues.get(input);
        if (amount.signum() < 0) throw new IllegalArgumentException("Negative cell input");
        if (amount.signum() == 0) return null;
        final InfinityCellRecord backing = record();
        if (backing == null || !input.getStackType()
            .getId()
            .equals(getStackType().getId())) return input;
        final BigInfinityCellRecord exact = (BigInfinityCellRecord) (Object) backing;
        if (!exact.canStoreStackBig(input)) return input;
        if (mode == Actionable.SIMULATE) {
            return null;
        }
        if (!exact.addStackBig(input, amount)) return input;
        markChanged();
        return null;
    }

    @Override
    public IAEStack<?> extractItemsBig(final IAEStack<?> request, final Actionable mode,
        final BaseActionSource source) {
        final BigInteger amount = BigAEStackValues.get(request);
        if (amount.signum() < 0) throw new IllegalArgumentException("Negative cell request");
        if (amount.signum() == 0) return null;
        final InfinityCellRecord backing = record();
        if (backing == null || !request.getStackType()
            .getId()
            .equals(getStackType().getId())) return null;
        final BigInteger removed = ((BigInfinityCellRecord) (Object) backing)
            .extractStackBig(request, amount, mode == Actionable.MODULATE);
        if (removed.signum() == 0) return null;
        if (mode == Actionable.MODULATE) markChanged();
        return BigAEStackValues.copyWithSize(request, removed);
    }

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$inject(final IAEStack<?> input, final Actionable mode, final BaseActionSource source,
        final CallbackInfoReturnable<IAEStack<?>> cir) {
        if (BigAEStackValues.isBig(input)) cir.setReturnValue(injectItemsBig(input, mode, source));
    }

    @Inject(method = "extractItems", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$extract(final IAEStack<?> request, final Actionable mode, final BaseActionSource source,
        final CallbackInfoReturnable<IAEStack<?>> cir) {
        if (BigAEStackValues.isBig(request)) cir.setReturnValue(extractItemsBig(request, mode, source));
    }

    @Inject(method = "getAvailableItems", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$available(final IItemList<?> out, final int iteration,
        final CallbackInfoReturnable<IItemList<?>> cir) {
        final InfinityCellRecord backing = record();
        cir.setReturnValue(
            backing == null ? out
                : ((BigInfinityCellRecord) (Object) backing).getAvailableStacksBig(getStackType(), out));
    }

    @Inject(method = "getAvailableItem", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$lookup(final IAEStack<?> request, final int iteration,
        final CallbackInfoReturnable<IAEStack<?>> cir) {
        final InfinityCellRecord backing = request == null || !request.getStackType()
            .getId()
            .equals(getStackType().getId()) ? null : record();
        final BigInteger amount = backing == null ? BigInteger.ZERO
            : ((BigInfinityCellRecord) (Object) backing).getAmountBig(request);
        cir.setReturnValue(amount.signum() == 0 ? null : BigAEStackValues.copyWithSize(request, amount));
    }
}
