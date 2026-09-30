package com.silvia.apeiron.ae.stack;

import java.io.IOException;
import java.math.BigInteger;

import appeng.api.storage.data.IAEItemStack;
import io.netty.buffer.ByteBuf;

/** Exact-count extensions mixed into AE2's actual AEItemStack class. */
public interface BigAEItemStack extends BigAEStack, BigAERequestableStack {

    BigInteger getStackSizeBig();

    IAEItemStack setStackSizeBig(BigInteger value);

    void incStackSizeBig(BigInteger amount);

    void decStackSizeBig(BigInteger amount);

    boolean isStackSizeBig();

    BigInteger getCountRequestableBig();

    IAEItemStack setCountRequestableBig(BigInteger value);

    void incCountRequestableBig(BigInteger amount);

    void decCountRequestableBig(BigInteger amount);

    boolean isCountRequestableBig();

    BigInteger getCountRequestableCraftsBig();

    IAEItemStack setCountRequestableCraftsBig(BigInteger value);

    void incCountRequestableCrafts(long amount);

    void incCountRequestableCraftsBig(BigInteger amount);

    boolean isCountRequestableCraftsBig();

    void addBig(IAEItemStack other);

    /** Separate exact-count wire format; legacy AE2 packets remain long-based. */
    void writeToBigPacket(ByteBuf out) throws IOException;
}
