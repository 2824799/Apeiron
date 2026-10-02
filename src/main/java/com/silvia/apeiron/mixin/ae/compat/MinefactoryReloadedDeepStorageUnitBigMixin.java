package com.silvia.apeiron.mixin.ae.compat;

import java.math.BigInteger;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.compat.BigReflectiveBackend;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigIMEInventory;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.integration.modules.helpers.MinefactoryReloadedDeepStorageUnit;

/** Keeps MFR deep-storage transfers exact before the DSU int setter boundary. */
@Mixin(value = MinefactoryReloadedDeepStorageUnit.class, remap = false)
public abstract class MinefactoryReloadedDeepStorageUnitBigMixin implements BigIMEInventory {

    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);

    private Object apeiron$dsu() {
        return BigReflectiveBackend.field(this, "dsu");
    }

    @Override
    public IAEItemStack injectItemsBig(final IAEItemStack input, final Actionable mode, final BaseActionSource source) {
        if (input == null) return null;
        final BigInteger requested = BigAEStackValues.get(input);
        if (requested.signum() <= 0) return null;
        final Object dsu = this.apeiron$dsu();
        final ItemStack storedType = (ItemStack) BigReflectiveBackend.invoke(dsu, "getStoredItemType");
        if (storedType != null && !input.equals(storedType)) return input;
        if (storedType == null && input.getTagCompound() != null) return input;

        final BigInteger stored = storedType == null ? BigInteger.ZERO : BigInteger.valueOf(storedType.stackSize);
        final BigInteger capacity = BigInteger
            .valueOf(BigReflectiveBackend.longValue(BigReflectiveBackend.invoke(dsu, "getMaxStoredCount")))
            .min(INT_MAX)
            .subtract(stored)
            .max(BigInteger.ZERO);
        final BigInteger accepted = requested.min(capacity);
        if (mode == Actionable.MODULATE && accepted.signum() > 0) {
            final int count = stored.add(accepted)
                .intValueExact();
            if (storedType == null) BigReflectiveBackend.invoke(dsu, "setStoredItemType", input.getItemStack(), count);
            else BigReflectiveBackend.invoke(dsu, "setStoredItemCount", count);
        }
        return accepted.compareTo(requested) < 0 ? BigAEStackValues.copyWithSize(input, requested.subtract(accepted))
            : null;
    }

    @Override
    public IAEItemStack extractItemsBig(final IAEItemStack request, final Actionable mode,
        final BaseActionSource source) {
        final Object dsu = this.apeiron$dsu();
        final ItemStack storedType = (ItemStack) BigReflectiveBackend.invoke(dsu, "getStoredItemType");
        if (request == null || storedType == null || !request.equals(storedType)) return null;
        final BigInteger available = BigInteger.valueOf(storedType.stackSize);
        final BigInteger extracted = BigAEStackValues.get(request)
            .min(available);
        if (extracted.signum() <= 0) return null;
        if (mode == Actionable.MODULATE) {
            BigReflectiveBackend.invoke(
                dsu,
                "setStoredItemCount",
                available.subtract(extracted)
                    .intValueExact());
        }
        return BigAEStackValues.copyWithSize(request, extracted);
    }

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$legacyInject(final IAEItemStack input, final Actionable mode, final BaseActionSource source,
        final CallbackInfoReturnable<IAEItemStack> cir) {
        if (BigAEStackValues.isBig(input)) cir.setReturnValue(this.injectItemsBig(input, mode, source));
    }

    @Inject(method = "extractItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$legacyExtract(final IAEItemStack request, final Actionable mode, final BaseActionSource source,
        final CallbackInfoReturnable<IAEItemStack> cir) {
        if (BigAEStackValues.isBig(request)) cir.setReturnValue(this.extractItemsBig(request, mode, source));
    }
}
