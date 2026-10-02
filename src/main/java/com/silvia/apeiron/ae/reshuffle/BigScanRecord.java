package com.silvia.apeiron.ae.reshuffle;

import java.math.BigInteger;

/** Exact storage-cell health values used by the reshuffle scanner. */
public interface BigScanRecord {

    BigInteger getTypesUsedBig();

    BigInteger getTypesTotalBig();

    BigInteger getBytesUsedBig();

    BigInteger getBytesTotalBig();

    boolean hasBigScanValues();

    void setExactScanValues(BigInteger typesUsed, BigInteger typesTotal, BigInteger bytesUsed, BigInteger bytesTotal);
}
