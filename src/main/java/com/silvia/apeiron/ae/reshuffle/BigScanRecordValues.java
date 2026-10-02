package com.silvia.apeiron.ae.reshuffle;

import java.math.BigInteger;

/** Thread-local bridge from CellInventory's exact capacity methods to ScanRecord constructors. */
public final class BigScanRecordValues {

    private static final ThreadLocal<BigInteger[]> CURRENT = new ThreadLocal<>();

    private BigScanRecordValues() {}

    public static void capture(final BigInteger typesUsed, final BigInteger typesTotal, final BigInteger bytesUsed,
        final BigInteger bytesTotal) {
        CURRENT.set(new BigInteger[] { typesUsed, typesTotal, bytesUsed, bytesTotal });
    }

    public static BigInteger[] current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
