package com.silvia.apeiron.ae.crafting.core;

import java.math.BigInteger;
import java.util.List;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;

/** Receives complete recipe batches atomically; simulation must not take ownership of inputs. */
public interface PatternBatchMedium {

    boolean pushPatternBig(ICraftingPatternDetails pattern, List<IAEStack<?>> inputs, BigInteger batches,
        boolean simulate);
}
