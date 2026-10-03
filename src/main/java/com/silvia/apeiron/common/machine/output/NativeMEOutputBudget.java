// Adapted from GT5-Unofficial ME output transaction checks, licensed under LGPL-3.0.
// See THIRD_PARTY_NOTICES.md and licenses/GT5-Unofficial-LGPL-3.0.txt.
package com.silvia.apeiron.common.machine.output;

import java.math.BigInteger;
import java.util.function.Supplier;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEStack;
import gregtech.common.tileentities.machines.outputme.base.MTEHatchOutputMEBase;

/** A copied cell and a finite native cache budget, shared across all outputs of a candidate batch. */
final class NativeMEOutputBudget<S extends IAEStack<S>> {

    private final MTEHatchOutputMEBase<S> provider;
    private final BaseActionSource source;
    private final IMEInventoryHandler<S> cell;
    private final boolean checkCell;
    private BigInteger room;

    NativeMEOutputBudget(MTEHatchOutputMEBase<S> provider, BaseActionSource source,
        Supplier<IMEInventoryHandler<S>> copyCell) {
        this.provider = provider;
        this.source = source;
        checkCell = provider.shouldCheckCell();
        cell = checkCell ? copyCell.get() : null;
        room = BigInteger.valueOf(Long.MAX_VALUE)
            .subtract(BigInteger.valueOf(Math.max(0, provider.getCachedAmount())));
        boolean dynamic = provider.getCheckMode() && (!provider.getCacheMode() || !provider.isDistribution())
            && !provider.canVoidOverflow();
        if (dynamic || (!provider.getCheckMode() && provider.getPhysicalSpace() <= 0))
            room = room.min(BigInteger.valueOf(Math.max(0, provider.getPhysicalSpace())));
    }

    BigInteger reserve(S type, BigInteger amount) {
        if (amount.signum() <= 0 || room.signum() <= 0 || !provider.canStore(type)) return BigInteger.ZERO;
        BigInteger accepted = amount.min(room);
        if (checkCell) {
            if (cell == null) return BigInteger.ZERO;
            S offered = type.copy();
            offered.setStackSize(accepted.longValueExact());
            S rejected = cell.injectItems(offered, Actionable.MODULATE, source);
            if (rejected != null) accepted = accepted.subtract(BigInteger.valueOf(rejected.getStackSize()))
                .max(BigInteger.ZERO);
        }
        room = room.subtract(accepted);
        return accepted;
    }
}
