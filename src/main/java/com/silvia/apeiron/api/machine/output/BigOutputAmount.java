package com.silvia.apeiron.api.machine.output;

import java.math.BigInteger;

/** Exact companion to an output entry whose original accessor remains a compatible long projection. */
public interface BigOutputAmount {

    BigInteger getOutputAmountBig();

    void setOutputAmountBig(BigInteger amount);
}
