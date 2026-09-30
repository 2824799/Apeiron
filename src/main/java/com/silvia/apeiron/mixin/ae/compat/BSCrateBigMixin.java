package com.silvia.apeiron.mixin.ae.compat;

import java.math.BigInteger;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigIMEInventory;
import com.silvia.apeiron.ae.compat.BigReflectiveBackend;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.integration.modules.helpers.BSCrate;

/** Chunks BetterStorage crate operations and keeps the untransferred amount exact. */
@Mixin(value = BSCrate.class, remap = false)
public abstract class BSCrateBigMixin implements BigIMEInventory {

    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);

    private Object apeiron$crateStorage() {
        return BigReflectiveBackend.field(this, "crateStorage");
    }

    @Override
    public IAEItemStack injectItemsBig(final IAEItemStack input, final Actionable mode,
        final BaseActionSource source) {
        if (input == null) return null;
        final Object crateStorage = this.apeiron$crateStorage();
        BigInteger remaining = BigAEStackValues.get(input);
        if (remaining.signum() <= 0) return null;
        if (mode == Actionable.SIMULATE) return null;

        while (remaining.signum() > 0) {
            final BigInteger offeredAmount = remaining.min(INT_MAX);
            final ItemStack offered = input.getItemStack();
            offered.stackSize = offeredAmount.intValueExact();
            final ItemStack failed = (ItemStack) BigReflectiveBackend.invoke(crateStorage, "insertItems", offered);
            final BigInteger rejected = failed == null ? BigInteger.ZERO : BigInteger.valueOf(failed.stackSize);
            if (rejected.signum() < 0 || rejected.compareTo(offeredAmount) > 0) {
                throw new IllegalStateException("BetterStorage returned an invalid insertion amount");
            }
            final BigInteger accepted = offeredAmount.subtract(rejected);
            remaining = remaining.subtract(accepted);
            if (accepted.signum() <= 0 || rejected.signum() > 0) break;
        }
        return remaining.signum() == 0 ? null : BigAEStackValues.copyWithSize(input, remaining);
    }

    @Override
    public IAEItemStack extractItemsBig(final IAEItemStack request, final Actionable mode,
        final BaseActionSource source) {
        if (request == null) return null;
        final Object crateStorage = this.apeiron$crateStorage();
        BigInteger remaining = BigAEStackValues.get(request);
        if (remaining.signum() <= 0) return null;
        if (mode == Actionable.SIMULATE) {
            final BigInteger available = BigInteger.valueOf(BigReflectiveBackend.intValue(
                BigReflectiveBackend.invoke(crateStorage, "getItemCount", request.getItemStack())));
            final BigInteger amount = remaining.min(available);
            return amount.signum() == 0 ? null : BigAEStackValues.copyWithSize(request, amount);
        }

        BigInteger extracted = BigInteger.ZERO;
        while (remaining.signum() > 0) {
            final BigInteger requestedAmount = remaining.min(INT_MAX);
            final ItemStack offered = request.getItemStack();
            offered.stackSize = requestedAmount.intValueExact();
            final ItemStack obtained = (ItemStack) BigReflectiveBackend.invoke(
                crateStorage, "extractItems", offered, requestedAmount.intValueExact());
            if (obtained == null || obtained.stackSize <= 0) break;
            final BigInteger amount = BigInteger.valueOf(obtained.stackSize).min(requestedAmount);
            extracted = extracted.add(amount);
            remaining = remaining.subtract(amount);
            if (amount.compareTo(requestedAmount) < 0) break;
        }
        return extracted.signum() == 0 ? null : BigAEStackValues.copyWithSize(request, extracted);
    }

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$legacyInject(final IAEItemStack input, final Actionable mode,
        final BaseActionSource source, final CallbackInfoReturnable<IAEItemStack> cir) {
        if (BigAEStackValues.isBig(input)) cir.setReturnValue(this.injectItemsBig(input, mode, source));
    }

    @Inject(method = "extractItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$legacyExtract(final IAEItemStack request, final Actionable mode,
        final BaseActionSource source, final CallbackInfoReturnable<IAEItemStack> cir) {
        if (BigAEStackValues.isBig(request)) cir.setReturnValue(this.extractItemsBig(request, mode, source));
    }
}
