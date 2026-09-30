package com.silvia.apeiron.ae.storage;

import java.math.BigInteger;

/** Exact network storage statistics used by the Network Status terminal. */
public interface BigStorageGrid {

    BigInteger getItemBytesTotalBig();

    BigInteger getItemBytesUsedBig();

    BigInteger getItemTypesTotalBig();

    BigInteger getItemTypesUsedBig();

    BigInteger getItemCellCountBig();

    BigInteger getFluidBytesTotalBig();

    BigInteger getFluidBytesUsedBig();

    BigInteger getFluidTypesTotalBig();

    BigInteger getFluidTypesUsedBig();

    BigInteger getFluidCellCountBig();

    BigInteger getEssentiaBytesTotalBig();

    BigInteger getEssentiaBytesUsedBig();

    BigInteger getEssentiaTypesTotalBig();

    BigInteger getEssentiaTypesUsedBig();

    BigInteger getEssentiaCellCountBig();
}
