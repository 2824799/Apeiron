package com.silvia.apeiron.ae.reshuffle;

import java.math.BigInteger;

/** Exact before/after counts for one storage-reshuffle report entry. */
public interface BigReshuffleItemChangeAccess {

    BigInteger getBeforeCountBig();

    BigInteger getAfterCountBig();

    BigInteger getDifferenceBig();

    void setCountsBig(BigInteger before, BigInteger after);
}
