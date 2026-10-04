package com.silvia.apeiron.api.machine.me.output;

import java.math.BigInteger;

import appeng.api.storage.data.IAEItemStack;

/** Exact counterpart of GregTech's physical-stack recipe output transaction. */
public interface BigItemOutputTransaction extends BigOutputTransaction {

    boolean storePartialBig(IAEItemStack input, BigInteger totalPerParallel, BigInteger perParallel);
}
