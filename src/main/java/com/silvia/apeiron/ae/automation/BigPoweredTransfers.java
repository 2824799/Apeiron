package com.silvia.apeiron.ae.automation;

import java.math.BigInteger;

import javax.annotation.Nonnull;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEFluidStack;
import appeng.me.storage.MEMonitorIFluidHandler;
import appeng.util.Platform;
import appeng.core.stats.Stats;
import appeng.api.networking.energy.IEnergySource;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigInventoryAdaptors;
import com.silvia.apeiron.ae.storage.BigMEInventories;

/** Exact, chunked equivalents of AE's long-only powered insert and extraction helpers. */
public final class BigPoweredTransfers {

    private static final BigInteger ZERO = BigInteger.ZERO;

    private BigPoweredTransfers() {}

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static IAEStack<?> poweredInsertBig(@Nonnull final IEnergySource energy,
        @Nonnull final IMEInventory cell, @Nonnull final IAEStack<?> input, @Nonnull final BaseActionSource source,
        @Nonnull final Actionable mode) {
        if (input == null || BigAEStackValues.get(input).signum() <= 0) return input;
        final int multiplier = Math.max(1, input.getAmountPerUnit());
        BigInteger remaining = BigAEStackValues.get(input);
        while (remaining.signum() > 0) {
            final BigInteger requestedChunk = remaining.min(BigInventoryAdaptors.MAX_EXTERNAL_CHUNK);
            final long requested = requestedChunk.longValue();
            final long energyUnits = ceilDiv(requested, multiplier);
            final double availablePower = energy.extractAEPower(energyUnits, Actionable.SIMULATE, PowerMultiplier.CONFIG);
            final long permitted = Math.min(requested, (long) (availablePower * multiplier + 0.9D));
            if (permitted <= 0) break;

            final IAEStack<?> chunk = BigAEStackValues.copyWithSize(input, BigInteger.valueOf(permitted));
            final IAEStack<?> leftover = BigMEInventories.injectItemsBig(cell, chunk, mode, source);
            final BigInteger left = leftover == null ? ZERO : BigAEStackValues.get(leftover);
            BigInventoryAdaptors.checkReturnedAmount(left, BigInteger.valueOf(permitted));
            final BigInteger inserted = BigInteger.valueOf(permitted).subtract(left);
            if (inserted.signum() <= 0) break;

            if (mode == Actionable.MODULATE) {
                energy.extractAEPower(
                        ceilDiv(inserted.longValueExact(), multiplier),
                        Actionable.MODULATE,
                        PowerMultiplier.CONFIG);
                recordInserted(source, inserted);
            }
            remaining = remaining.subtract(inserted);
            if (left.signum() > 0) break;
        }
        return remaining.signum() == 0 ? null : BigAEStackValues.copyWithSize(input, remaining);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static IAEStack<?> poweredExtractionBig(@Nonnull final IEnergySource energy,
        @Nonnull final IMEInventory cell, @Nonnull final IAEStack<?> request, @Nonnull final BaseActionSource source,
        @Nonnull final Actionable mode) {
        if (request == null || BigAEStackValues.get(request).signum() <= 0) return null;
        final int multiplier = Math.max(1, request.getAmountPerUnit());
        BigInteger remaining = BigAEStackValues.get(request);
        BigInteger extracted = ZERO;
        while (remaining.signum() > 0) {
            final BigInteger requestedChunk = remaining.min(BigInventoryAdaptors.MAX_EXTERNAL_CHUNK);
            final long requested = requestedChunk.longValue();
            final long energyUnits = ceilDiv(requested, multiplier);
            final double availablePower = energy.extractAEPower(energyUnits, Actionable.SIMULATE, PowerMultiplier.CONFIG);
            final long permitted = Math.min(requested, (long) (availablePower * multiplier + 0.9D));
            if (permitted <= 0) break;

            final IAEStack<?> chunk = BigAEStackValues.copyWithSize(request, BigInteger.valueOf(permitted));
            final IAEStack<?> result = BigMEInventories.extractItemsBig(cell, chunk, mode, source);
            if (result == null || BigAEStackValues.get(result).signum() <= 0) break;
            final BigInteger amount = BigAEStackValues.get(result).min(BigInteger.valueOf(permitted));
            if (mode == Actionable.MODULATE) {
                energy.extractAEPower(
                        ceilDiv(amount.longValueExact(), multiplier),
                        Actionable.MODULATE,
                        PowerMultiplier.CONFIG);
                recordExtracted(source, amount);
            }
            extracted = extracted.add(amount);
            remaining = remaining.subtract(amount);
            if (amount.compareTo(BigInteger.valueOf(permitted)) < 0) break;
        }
        return extracted.signum() == 0 ? null : BigAEStackValues.copyWithSize(request, extracted);
    }

    private static long ceilDiv(final long value, final int divisor) {
        return (value + divisor - 1L) / divisor;
    }

    private static void recordInserted(final BaseActionSource source, final BigInteger amount) {
        if (source instanceof PlayerSource player && amount.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0) {
            Stats.ItemsInserted.addToPlayer(player.player, amount.intValue());
        }
    }

    private static void recordExtracted(final BaseActionSource source, final BigInteger amount) {
        if (source instanceof PlayerSource player && amount.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0) {
            Stats.ItemsExtracted.addToPlayer(player.player, amount.intValue());
        }
    }
}
