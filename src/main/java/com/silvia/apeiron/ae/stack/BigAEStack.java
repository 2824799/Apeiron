package com.silvia.apeiron.ae.stack;

import java.math.BigInteger;

import appeng.api.storage.data.IAEStack;

/** Exact stack-size access for AE stacks that are backed by Apeiron. */
public interface BigAEStack {

    BigInteger getStackSizeBig();

    IAEStack<?> setStackSizeBig(BigInteger value);

    void incStackSizeBig(BigInteger amount);

    void decStackSizeBig(BigInteger amount);

    boolean isStackSizeBig();
}
