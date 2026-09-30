package com.silvia.apeiron.ae.crafting.core;

import java.math.BigInteger;

import appeng.api.config.Actionable;
import appeng.api.storage.data.IAEStack;

/** Exact operations for AE's crafting calculation inventory. */
public interface BigMECraftingInventory {

    IAEStack<?> extractItemsBig(IAEStack<?> request, Actionable mode);

    void injectItemsBig(IAEStack<?> input, Actionable mode);

    default IAEStack<?> extractItemsBig(final IAEStack<?> request, final Actionable mode, final BigInteger ignored) {
        return extractItemsBig(request, mode);
    }
}
