package com.silvia.apeiron.ae.storage;

import java.math.BigInteger;

/** Exact capacity view exposed by AE's item and fluid cell handlers. */
public interface BigCellInventoryHandler {

    BigInteger getStoredItemCountBig();

    BigInteger getTotalBytesBig();

    BigInteger getUsedBytesBig();

    BigInteger getFreeBytesBig();

    BigInteger getTotalTypesBig();

    BigInteger getUsedTypesBig();

    BigInteger getFreeTypesBig();
}
