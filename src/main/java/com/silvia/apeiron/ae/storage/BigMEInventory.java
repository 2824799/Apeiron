package com.silvia.apeiron.ae.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;

/** Exact-count inventory operations for every AE stack type. */
public interface BigMEInventory {

    /** Return the exact uninserted remainder, or null when every stack was accepted. */
    IAEStack<?> injectItemsBig(IAEStack<?> input, Actionable mode, BaseActionSource source);

    /** Return the exact extracted stack, or null when nothing was available. */
    IAEStack<?> extractItemsBig(IAEStack<?> request, Actionable mode, BaseActionSource source);
}
