package com.silvia.apeiron.api.machine.me.output;

import java.math.BigInteger;

import appeng.api.storage.data.IAEFluidStack;

/** Exact fluid quantities, measured in mB, alongside GregTech's original transaction API. */
public interface BigFluidOutputTransaction extends BigOutputTransaction {

    boolean storePartialBig(IAEFluidStack input, BigInteger totalPerParallel, BigInteger perParallel);
}
