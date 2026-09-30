package com.silvia.apeiron.ae.stack;

import java.math.BigInteger;

import appeng.api.storage.data.IAEStack;

/** Exact requestable and requestable-crafts values for generic AE stacks. */
public interface BigAERequestableStack {

    BigInteger getCountRequestableBig();

    IAEStack<?> setCountRequestableBig(BigInteger value);

    void incCountRequestableBig(BigInteger amount);

    void decCountRequestableBig(BigInteger amount);

    boolean isCountRequestableBig();

    BigInteger getCountRequestableCraftsBig();

    IAEStack<?> setCountRequestableCraftsBig(BigInteger value);

    void incCountRequestableCraftsBig(BigInteger amount);

    boolean isCountRequestableCraftsBig();
}
