package com.silvia.apeiron.api.machine.me.output;

import java.math.BigInteger;

import appeng.api.storage.data.IAEFluidStack;
import gregtech.api.interfaces.IOutputHatchTransaction;

/** Exact fluid quantities, measured in mB, alongside GregTech's original transaction API. */
public interface BigFluidOutputTransaction extends IOutputHatchTransaction {

    boolean storePartialBig(IAEFluidStack input, BigInteger totalPerParallel, BigInteger perParallel);
}
