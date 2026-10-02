package com.silvia.apeiron.ae.sync;

import java.io.IOException;
import java.math.BigInteger;

import com.silvia.apeiron.math.BigValueCodec;

import appeng.container.sync.SyncCodec;
import io.netty.buffer.ByteBuf;

/** Exact BigInteger codec for AE's object synchronization handlers. */
public enum BigIntegerSyncCodec implements SyncCodec<BigInteger> {

    INSTANCE;

    @Override
    public void write(final ByteBuf buf, final BigInteger value) throws IOException {
        if (value == null) {
            throw new IOException("null BigInteger cannot be synchronized");
        }
        BigValueCodec.writePacket(buf, value);
    }

    @Override
    public BigInteger read(final ByteBuf buf) throws IOException {
        return BigValueCodec.readPacket(buf);
    }

    @Override
    public BigInteger copy(final BigInteger value) {
        return value;
    }

    @Override
    public boolean valuesEqual(final BigInteger left, final BigInteger right) {
        return left.equals(right);
    }

    @Override
    public String getTypeKey() {
        return "apeiron-big-integer";
    }
}
