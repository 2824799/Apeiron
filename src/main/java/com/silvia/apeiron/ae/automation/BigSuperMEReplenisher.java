package com.silvia.apeiron.ae.automation;

import java.math.BigInteger;

/** Exact storage counters for the Super ME Replenisher UI and transfer logic. */
public interface BigSuperMEReplenisher {

    BigInteger getTotalBytesBig();

    BigInteger getUsedBytesBig();
}
