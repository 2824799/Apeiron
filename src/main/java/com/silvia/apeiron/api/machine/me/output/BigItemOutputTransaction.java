package com.silvia.apeiron.api.machine.me.output;

import java.math.BigInteger;

import appeng.api.storage.data.IAEItemStack;
import gregtech.api.interfaces.IOutputBusTransaction;

/** Exact counterpart of GregTech's physical-stack recipe output transaction. */
public interface BigItemOutputTransaction extends IOutputBusTransaction {

    boolean storePartialBig(IAEItemStack input, BigInteger totalPerParallel, BigInteger perParallel);
}
