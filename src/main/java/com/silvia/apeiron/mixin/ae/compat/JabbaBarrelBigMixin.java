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
import appeng.integration.modules.helpers.JabbaBarrel;

/** Keeps Jabba barrel transfers exact before converting to the barrel API's int setter. */
@Mixin(value = JabbaBarrel.class, remap = false)
public abstract class JabbaBarrelBigMixin implements BigIMEInventory {

    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);

    private Object apeiron$barrel() {
        return BigReflectiveBackend.field(this, "barrel");
    }

    private BigInteger apeiron$backendMaximum(final BigInteger exposedMaximum) {
        return exposedMaximum.min(INT_MAX);
    }

    @Override
    public IAEItemStack injectItemsBig(final IAEItemStack input, final Actionable mode, final BaseActionSource source) {
        if (input == null) return null;
        final BigInteger requested = BigAEStackValues.get(input);
        if (requested.signum() <= 0) return null;
        final Object barrel = this.apeiron$barrel();
        final ItemStack storedType = (ItemStack) BigReflectiveBackend.invoke(barrel, "getStoredItemType");
        if (storedType != null && !input.equals(storedType)) return input;
        if (storedType == null && input.getTagCompound() != null) return input;

        final BigInteger stored = storedType == null ? BigInteger.ZERO : BigInteger.valueOf(storedType.stackSize);
        final Object storage = BigReflectiveBackend.invoke(barrel, "getStorage");
        final BigInteger max = storedType == null
            ? BigInteger.valueOf(BigReflectiveBackend.longValue(BigReflectiveBackend.invoke(storage, "getMaxStacks")))
                .multiply(
                    BigInteger.valueOf(
                        input.getItemStack()
                            .getMaxStackSize()))
            : BigInteger
                .valueOf(BigReflectiveBackend.longValue(BigReflectiveBackend.invoke(barrel, "getMaxStoredCount")));
        final BigInteger capacity = this.apeiron$backendMaximum(max)
            .subtract(stored)
            .max(BigInteger.ZERO);
        final boolean isVoid = BigReflectiveBackend.booleanValue(BigReflectiveBackend.invoke(storage, "isVoid"));
        final BigInteger accepted = isVoid ? requested : requested.min(capacity);
        if (mode == Actionable.MODULATE && accepted.signum() > 0) {
            final int count = stored.add(accepted.min(INT_MAX))
                .min(INT_MAX)
                .intValueExact();
            if (storedType == null)
                BigReflectiveBackend.invoke(barrel, "setStoredItemType", input.getItemStack(), count);
            else BigReflectiveBackend.invoke(barrel, "setStoredItemCount", count);
        }
        return accepted.compareTo(requested) < 0 ? BigAEStackValues.copyWithSize(input, requested.subtract(accepted))
            : null;
    }

    @Override
    public IAEItemStack extractItemsBig(final IAEItemStack request, final Actionable sourceMode,
        final BaseActionSource source) {
        final Object barrel = this.apeiron$barrel();
        final ItemStack storedType = (ItemStack) BigReflectiveBackend.invoke(barrel, "getStoredItemType");
        if (request == null || storedType == null || !request.equals(storedType)) return null;
        final BigInteger available = BigInteger.valueOf(storedType.stackSize);
        final BigInteger extracted = BigAEStackValues.get(request)
            .min(available);
        if (extracted.signum() <= 0) return null;
        if (sourceMode == Actionable.MODULATE) {
            BigReflectiveBackend.invoke(
                barrel,
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
