package com.silvia.apeiron.mixin.ae.compat;

import java.math.BigInteger;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigIMEInventory;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.integration.abstraction.IFZ;
import appeng.integration.modules.helpers.FactorizationBarrel;

/** Keeps Factorization barrel transfers exact until the barrel's own int boundary. */
@Mixin(value = FactorizationBarrel.class, remap = false)
public abstract class FactorizationBarrelBigMixin implements BigIMEInventory {

    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);

    @Shadow
    @Final
    private IFZ fProxy;
    @Shadow
    @Final
    private TileEntity te;

    @Override
    public IAEItemStack injectItemsBig(final IAEItemStack input, final Actionable mode, final BaseActionSource source) {
        if (input == null) return null;
        final BigInteger requested = BigAEStackValues.get(input);
        if (requested.signum() <= 0) return null;
        final ItemStack template = input.getItemStack();
        if (template == null || template.isItemDamaged()) return input;

        final ItemStack current = this.fProxy.barrelGetItem(this.te);
        if (current != null && !input.equals(current)) return input;

        final BigInteger stored = BigInteger.valueOf(this.fProxy.barrelGetItemCount(this.te));
        final BigInteger capacity = BigInteger.valueOf(this.fProxy.barrelGetMaxItemCount(this.te))
            .subtract(stored)
            .max(BigInteger.ZERO);
        final BigInteger accepted = requested.min(capacity)
            .min(INT_MAX);
        if (mode == Actionable.MODULATE && accepted.signum() > 0) {
            if (current == null) this.fProxy.setItemType(this.te, template);
            this.fProxy.barrelSetCount(
                this.te,
                stored.add(accepted)
                    .intValueExact());
        }
        return accepted.signum() == 0 || accepted.compareTo(requested) < 0
            ? BigAEStackValues.copyWithSize(input, requested.subtract(accepted))
            : null;
    }

    @Override
    public IAEItemStack extractItemsBig(final IAEItemStack request, final Actionable mode,
        final BaseActionSource source) {
        if (request == null) return null;
        final ItemStack current = this.fProxy.barrelGetItem(this.te);
        if (current == null || !request.equals(current)) return null;
        final BigInteger available = BigInteger.valueOf(this.fProxy.barrelGetItemCount(this.te));
        final BigInteger extracted = BigAEStackValues.get(request)
            .min(available)
            .min(INT_MAX);
        if (extracted.signum() <= 0) return null;
        if (mode == Actionable.MODULATE) {
            final int remaining = available.subtract(extracted)
                .intValueExact();
            this.fProxy.barrelSetCount(this.te, remaining);
            if (remaining == 0) this.fProxy.setItemType(this.te, null);
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
