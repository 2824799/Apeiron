package com.silvia.apeiron.ae.crafting.core;

import java.math.BigInteger;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;

/** Exact production input and progress counters of a crafting CPU. */
public interface BigCraftingCPUState {
    IAEStack<?> injectItemsBig(IAEStack<?> input, Actionable mode, BaseActionSource source);
    BigInteger getStartItemCountBig();
    BigInteger getRemainingItemCountBig();
}
