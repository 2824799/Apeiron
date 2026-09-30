package com.silvia.apeiron.math;

import java.io.IOException;
import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;

import io.netty.buffer.ByteBuf;

/** Version-independent codecs for a legacy long plus an optional exact value. */
public final class BigValueCodec {

    private BigValueCodec() {}

    public static void writeNBT(NBTTagCompound tag, String legacyKey, String bigKey, AdaptiveInteger value) {
        tag.setLong(legacyKey, value.longValueSaturated());
        if (value.isBig()) {
            tag.setByteArray(
                bigKey,
                value.toBigInteger()
                    .toByteArray());
        } else {
            tag.removeTag(bigKey);
        }
    }

    public static AdaptiveInteger readNBT(NBTTagCompound tag, String legacyKey, String bigKey) {
        if (tag.hasKey(bigKey) && !tag.hasKey(bigKey, 7)) {
            throw new IllegalArgumentException("invalid big integer NBT type for key " + bigKey);
        }
        if (tag.hasKey(bigKey, 7)) {
            byte[] bytes = tag.getByteArray(bigKey);
            if (bytes.length == 0) {
                throw new IllegalArgumentException("empty big integer in NBT key " + bigKey);
            }
            return new AdaptiveInteger(new BigInteger(bytes));
        }
        return new AdaptiveInteger(tag.getLong(legacyKey));
    }

    public static void writePacket(ByteBuf out, BigInteger value) {
        byte[] bytes = value.toByteArray();
        out.writeInt(bytes.length);
        out.writeBytes(bytes);
    }

    public static BigInteger readPacket(ByteBuf in) throws IOException {
        if (in.readableBytes() < Integer.BYTES) {
            throw new IOException("missing big integer length");
        }
        int length = in.readInt();
        if (length < 1 || length > in.readableBytes()) {
            throw new IOException("invalid big integer length: " + length);
        }
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return new BigInteger(bytes);
    }
}
