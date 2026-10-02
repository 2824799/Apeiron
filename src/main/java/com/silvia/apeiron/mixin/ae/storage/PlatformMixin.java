package com.silvia.apeiron.mixin.ae.storage;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigMEInventories;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEStack;
import appeng.util.Platform;

/** Routes AE's powered item and fluid transfers through exact stack counts. */
@Mixin(value = Platform.class, remap = false)
public abstract class PlatformMixin {

    @Inject(
        method = "poweredExtraction(Lappeng/api/networking/energy/IEnergySource;Lappeng/api/storage/IMEInventory;Lappeng/api/storage/data/IAEStack;Lappeng/api/networking/security/BaseActionSource;Lappeng/api/config/Actionable;)Lappeng/api/storage/data/IAEStack;",
        at = @At("HEAD"),
        cancellable = true)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void apeiron$poweredExtraction(final IEnergySource energy, final IMEInventory cell,
        final IAEStack request, final BaseActionSource source, final Actionable mode,
        final CallbackInfoReturnable<IAEStack> cir) {
        if (!BigAEStackValues.isBig(request)) return;

        final int multiplier = request.getAmountPerUnit();
        final BigInteger requested = BigAEStackValues.get(request);
        final BigInteger units = apeiron$ceilDiv(requested, multiplier);
        final double availablePower = energy
            .extractAEPower(units.doubleValue(), Actionable.SIMULATE, PowerMultiplier.CONFIG);
        final BigInteger itemToExtract = apeiron$powerBound(availablePower, multiplier, requested);
        if (itemToExtract.signum() <= 0) {
            cir.setReturnValue(null);
            return;
        }

        final IAEStack toExtract = BigAEStackValues.set(request.copy(), itemToExtract);
        final IAEStack result = BigMEInventories.extractItemsBig(cell, toExtract, mode, source);
        if (mode == Actionable.MODULATE && result != null) {
            energy.extractAEPower(
                apeiron$ceilDiv(BigAEStackValues.get(result), multiplier).doubleValue(),
                Actionable.MODULATE,
                PowerMultiplier.CONFIG);
        }
        cir.setReturnValue(result);
    }

    @Inject(
        method = "poweredInsert(Lappeng/api/networking/energy/IEnergySource;Lappeng/api/storage/IMEInventory;Lappeng/api/storage/data/IAEStack;Lappeng/api/networking/security/BaseActionSource;Lappeng/api/config/Actionable;)Lappeng/api/storage/data/IAEStack;",
        at = @At("HEAD"),
        cancellable = true)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void apeiron$poweredInsert(final IEnergySource energy, final IMEInventory cell, final IAEStack input,
        final BaseActionSource source, final Actionable mode, final CallbackInfoReturnable<IAEStack> cir) {
        if (!BigAEStackValues.isBig(input)) return;

        final int multiplier = input.getAmountPerUnit();
        final BigInteger requested = BigAEStackValues.get(input);
        final BigInteger units = apeiron$ceilDiv(requested, multiplier);
        final double availablePower = energy
            .extractAEPower(units.doubleValue(), Actionable.SIMULATE, PowerMultiplier.CONFIG);
        final BigInteger itemToAdd = apeiron$powerBound(availablePower, multiplier, requested);
        if (itemToAdd.signum() <= 0) {
            cir.setReturnValue(input);
            return;
        }

        final IAEStack toInsert = BigAEStackValues.set(input.copy(), itemToAdd);
        final IAEStack leftover = BigMEInventories.injectItemsBig(cell, toInsert, mode, source);
        final BigInteger inserted = itemToAdd
            .subtract(leftover == null ? BigInteger.ZERO : BigAEStackValues.get(leftover));
        if (inserted.signum() <= 0) {
            cir.setReturnValue(input);
            return;
        }

        if (mode == Actionable.MODULATE) {
            energy.extractAEPower(
                apeiron$ceilDiv(inserted, multiplier).doubleValue(),
                Actionable.MODULATE,
                PowerMultiplier.CONFIG);
        }
        if (inserted.compareTo(requested) >= 0) {
            cir.setReturnValue(null);
            return;
        }

        final BigInteger remaining = requested.subtract(inserted);
        final IAEStack result = leftover == null ? input.copy() : leftover;
        cir.setReturnValue(BigAEStackValues.set(result, remaining));
    }

    private static BigInteger apeiron$ceilDiv(final BigInteger value, final int divisor) {
        return value.add(BigInteger.valueOf(divisor - 1L))
            .divide(BigInteger.valueOf(divisor));
    }

    private static BigInteger apeiron$powerBound(final double availablePower, final int multiplier,
        final BigInteger requested) {
        if (!(availablePower > 0.0)) return BigInteger.ZERO;
        if (Double.isInfinite(availablePower) || Double.isNaN(availablePower)) return requested;
        final BigDecimal poweredItems = BigDecimal.valueOf(availablePower)
            .multiply(BigDecimal.valueOf(multiplier))
            .add(BigDecimal.valueOf(0.9));
        if (poweredItems.signum() <= 0) return BigInteger.ZERO;
        return poweredItems.toBigInteger()
            .min(requested);
    }
}
