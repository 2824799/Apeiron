package com.silvia.apeiron.mixin.ae.automation;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.automation.BigPartTargetAccess;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigInventoryAdaptors;

import appeng.api.config.Actionable;
import appeng.api.config.InsertionMode;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.storage.data.IAEStack;
import appeng.me.GridAccessException;
import appeng.parts.automation.PartBaseExportBus;
import appeng.util.InventoryAdaptor;

/** Sends oversized crafted results to ordinary Minecraft inventories without truncating them to long. */
@Mixin(value = PartBaseExportBus.class, remap = false)
public abstract class PartBaseExportBusMixin {

    @Inject(method = "injectCraftedItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$injectBig(final ICraftingLink link, final IAEStack<?> items, final Actionable mode,
        final CallbackInfoReturnable<IAEStack<?>> cir) {
        if (!BigAEStackValues.isBig(items)) return;
        final PartBaseExportBus<?> self = (PartBaseExportBus<?>) (Object) this;
        final BigPartTargetAccess access = (BigPartTargetAccess) (Object) this;
        if (!(access.apeiron$getTarget() instanceof InventoryAdaptor adaptor)) {
            cir.setReturnValue(items);
            return;
        }

        try {
            if (!self.getProxy()
                .isActive()) {
                cir.setReturnValue(items);
                return;
            }
            final IEnergyGrid energy = self.getProxy()
                .getEnergy();
            final int multiplier = items.getAmountPerUnit();
            final BigInteger requested = BigAEStackValues.get(items);
            final BigInteger requestedUnits = requested.add(BigInteger.valueOf(multiplier - 1L))
                .divide(BigInteger.valueOf(multiplier));
            final double requiredPower = requestedUnits.doubleValue();
            final double availablePower = energy.extractAEPower(requiredPower, mode, PowerMultiplier.CONFIG);

            BigInteger allowed = requested;
            if (!Double.isInfinite(availablePower) && !Double.isNaN(availablePower)
                && availablePower + 0.01 < requiredPower) {
                allowed = BigDecimal.valueOf(Math.max(0.0, availablePower))
                    .multiply(BigDecimal.valueOf(multiplier))
                    .toBigInteger()
                    .min(requested);
            }
            if (allowed.signum() <= 0) {
                cir.setReturnValue(items);
                return;
            }

            final IAEStack<?> offered = BigAEStackValues.copyWithSize(items, allowed);
            final IAEStack<?> leftover = BigInventoryAdaptors
                .addStackBig(adaptor, offered, InsertionMode.DEFAULT, mode == Actionable.SIMULATE);
            final BigInteger left = leftover == null ? BigInteger.ZERO : BigAEStackValues.get(leftover);
            final BigInteger inserted = allowed.subtract(left)
                .max(BigInteger.ZERO);
            final BigInteger remaining = requested.subtract(inserted);
            cir.setReturnValue(remaining.signum() == 0 ? null : BigAEStackValues.copyWithSize(items, remaining));
        } catch (final GridAccessException e) {
            cir.setReturnValue(items);
        }
    }
}
