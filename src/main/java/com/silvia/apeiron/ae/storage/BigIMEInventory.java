package com.silvia.apeiron.ae.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;

/** Exact-count item input/output on inventories that can honor it. */
public interface BigIMEInventory {

    /** Return the exact uninserted remainder, or null when every item was accepted. */
    IAEItemStack injectItemsBig(IAEItemStack input, Actionable mode, BaseActionSource source);

    /** Return the exact extracted amount, or null when no items were available. */
    IAEItemStack extractItemsBig(IAEItemStack request, Actionable mode, BaseActionSource source);

}
