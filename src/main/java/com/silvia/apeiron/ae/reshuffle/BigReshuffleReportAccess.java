package com.silvia.apeiron.ae.reshuffle;

import java.math.BigInteger;

/** Exact counters exposed by an AE storage-reshuffle report. */
public interface BigReshuffleReportAccess {

    BigInteger getExtractedItemsBig();

    BigInteger getInjectedItemsBig();

    BigInteger getBeforeItemsBig();

    BigInteger getAfterItemsBig();

    void setTransferTotalsBig(BigInteger extracted, BigInteger injected);
}
