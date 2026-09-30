package com.silvia.apeiron.ae.flow;

import java.math.BigInteger;

/** Exact item-flow totals attached to AE2's legacy FlowRate object. */
public interface BigFlowRate {

    BigInteger inBig();

    BigInteger outBig();

    BigInteger netBig();

    boolean isBigFlow();

    void setBigFlow(BigInteger in, BigInteger out);
}
