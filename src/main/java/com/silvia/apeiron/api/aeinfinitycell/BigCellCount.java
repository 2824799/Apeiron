package com.silvia.apeiron.api.aeinfinitycell;

import java.math.BigInteger;

/** Exact operations alongside Infinity Cell's original long counter API. */
public interface BigCellCount {

    void addAmountBig(BigInteger amount);

    BigInteger extractAmountBig(BigInteger requested);
}
