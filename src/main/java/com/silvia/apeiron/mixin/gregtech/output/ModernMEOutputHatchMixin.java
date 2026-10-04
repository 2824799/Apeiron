package com.silvia.apeiron.mixin.gregtech.output;

import org.spongepowered.asm.mixin.Mixin;

import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputHatch;
import com.silvia.apeiron.compat.NativeMEOutputTransactions;

import gregtech.api.enums.OutputHatchType;
import gregtech.api.interfaces.IOutputHatch;
import gregtech.api.interfaces.IOutputHatchTransaction;

/** Adds the native fluid transaction API only on GT releases that provide it. */
@Mixin(value = MTEBoundlessMEOutputHatch.class, remap = false)
public abstract class ModernMEOutputHatchMixin implements IOutputHatch {

    @Override
    public OutputHatchType getHatchType() {
        MTEBoundlessMEOutputHatch hatch = (MTEBoundlessMEOutputHatch) (Object) this;
        if (hatch.getProvider()
            .getCacheMode())
            return hatch.isFiltered() ? OutputHatchType.MECacheFiltered : OutputHatchType.MECacheUnfiltered;
        return hatch.isFiltered() ? OutputHatchType.MEFiltered : OutputHatchType.MEUnfiltered;
    }

    @Override
    public IOutputHatchTransaction createTransaction() {
        MTEBoundlessMEOutputHatch hatch = (MTEBoundlessMEOutputHatch) (Object) this;
        return (IOutputHatchTransaction) NativeMEOutputTransactions.wrap(hatch, hatch.createTransactionBig(), true);
    }
}
