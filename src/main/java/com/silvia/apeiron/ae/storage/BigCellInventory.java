package com.silvia.apeiron.ae.storage;

import java.math.BigInteger;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;

/** Exact accounting alongside AE2's existing capacity and count getters. */
public interface BigCellInventory extends BigIMEInventory {

    boolean hasDistributionCard();

    boolean hasOverflowCard();

    BigInteger getStoredItemCountBig();

    BigInteger getTotalTypesBig();

    BigInteger getStoredTypesBig();

    BigInteger getRemainingTypesBig();

    BigInteger getTotalBytesBig();

    BigInteger getUsedBytesBig();

    BigInteger getFreeBytesBig();

    BigInteger getRemainingItemCountBig();

    BigInteger getRemainingItemsCountDistBig(IAEItemStack stack);

    /** Exact distribution-card capacity for either an item or a fluid stack. */
    BigInteger getRemainingItemsCountDistBig(IAEStack<?> stack);
}
