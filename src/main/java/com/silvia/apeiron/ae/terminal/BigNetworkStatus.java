package com.silvia.apeiron.ae.terminal;

import java.math.BigInteger;

/** Exact values synchronized to the client-side Network Status container. */
public interface BigNetworkStatus {

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
