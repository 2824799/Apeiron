package com.silvia.apeiron.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class BigValueCodecTest {

    @Test
    public void nbtRetainsExactCountAndLegacyLong() {
        NBTTagCompound tag = new NBTTagCompound();
        BigInteger exact = BigInteger.ONE.shiftLeft(128)
            .add(BigInteger.valueOf(37L));
        BigValueCodec.writeNBT(tag, "Cnt", "ApeironCnt", new AdaptiveInteger(exact));
        assertEquals(Long.MAX_VALUE, tag.getLong("Cnt"));
        assertTrue(tag.hasKey("ApeironCnt", 7));
        assertEquals(
            exact,
            BigValueCodec.readNBT(tag, "Cnt", "ApeironCnt")
                .toBigInteger());

        BigValueCodec.writeNBT(tag, "Cnt", "ApeironCnt", new AdaptiveInteger(15L));
        assertFalse(tag.hasKey("ApeironCnt", 7));
        assertEquals(
            15L,
            BigValueCodec.readNBT(tag, "Cnt", "ApeironCnt")
                .longValueExact());
    }

    @Test
    public void malformedExactCountDoesNotFallBackToClampedLong() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("Cnt", Long.MAX_VALUE);
        tag.setString("ApeironCnt", "wrong type");
        try {
            BigValueCodec.readNBT(tag, "Cnt", "ApeironCnt");
            throw new AssertionError("malformed exact count was accepted");
        } catch (IllegalArgumentException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("ApeironCnt"));
        }
    }

    @Test
    public void packetRoundTripPreservesSignsAndRejectsTruncation() throws IOException {
        ByteBuf out = Unpooled.buffer();
        BigInteger exact = BigInteger.ONE.shiftLeft(256)
            .negate();
        BigValueCodec.writePacket(out, exact);
        assertEquals(exact, BigValueCodec.readPacket(out));
        out.release();

        ByteBuf truncated = Unpooled.buffer();
        truncated.writeInt(20);
        truncated.writeByte(1);
        try {
            BigValueCodec.readPacket(truncated);
            throw new AssertionError("truncated payload was accepted");
        } catch (IOException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("length"));
        } finally {
            truncated.release();
        }
    }
}
