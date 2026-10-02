package com.silvia.apeiron.ae.reshuffle;

import java.math.BigInteger;
import java.util.List;

import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEStack;

/** Access to the private reshuffle queue entry after it is mixed into AE2. */
public interface BigReshufflePending {

    IAEStack<?> getStackBig();

    void setStackBig(IAEStack<?> stack);

    List<?> getSourcesBig();

    void addSourceBig(IMEInventoryHandler source, BigInteger amount);
}
