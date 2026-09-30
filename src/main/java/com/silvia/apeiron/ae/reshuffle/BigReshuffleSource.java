package com.silvia.apeiron.ae.reshuffle;

import java.math.BigInteger;

import appeng.api.storage.IMEInventoryHandler;

/** Exact source contribution stored alongside a reshuffle rollback entry. */
public interface BigReshuffleSource {

    IMEInventoryHandler getSourceBig();

    BigInteger getAmountBig();

    void setAmountBig(BigInteger amount);
}
