package com.silvia.apeiron.ae.flow;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Map;

import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.api.storage.data.IAEStack;
import appeng.me.cache.ItemFlowGridCache.FlowRate;
import appeng.util.Platform;
import io.netty.buffer.ByteBuf;

/** Optional exact section appended to AE2's flow-rate packet. */
public final class BigFlowPackets {

    public static final int PACKET_MAGIC = 0x41504652;

    private BigFlowPackets() {}

    public static boolean hasBigValues(final Map<IAEStack<?>, ?> rates) {
        for (final Object rate : rates.values()) {
            if (rate instanceof BigFlowRate && ((BigFlowRate) rate).isBigFlow()) return true;
        }
        return false;
    }

    public static void write(final ByteBuf out, final Map<IAEStack<?>, ?> rates) {
        int count = 0;
        for (final Object rate : rates.values()) {
            if (rate instanceof BigFlowRate && ((BigFlowRate) rate).isBigFlow()) count++;
        }
        if (count == 0) return;

        out.writeInt(PACKET_MAGIC);
        out.writeInt(count);
        for (final Map.Entry<IAEStack<?>, ?> entry : rates.entrySet()) {
            if (!(entry.getValue() instanceof BigFlowRate) || !((BigFlowRate) entry.getValue()).isBigFlow()) continue;
            final BigFlowRate exact = (BigFlowRate) entry.getValue();
            Platform.writeStackByte(entry.getKey(), out);
            com.silvia.apeiron.math.BigValueCodec.writePacket(out, exact.inBig());
            com.silvia.apeiron.math.BigValueCodec.writePacket(out, exact.outBig());
        }
    }

    public static void read(final ByteBuf in, final Map<IAEStack<?>, FlowRate> rates) throws IOException {
        if (in.readableBytes() < Integer.BYTES * 2 || in.getInt(in.readerIndex()) != PACKET_MAGIC) return;
        in.skipBytes(Integer.BYTES);
        final int count = in.readInt();
        if (count < 0 || count > 1_000_000) throw new IOException("invalid Apeiron flow-rate count: " + count);
        for (int i = 0; i < count; i++) {
            final IAEStack<?> stack = Platform.readStackByte(in);
            final BigInteger inRate = com.silvia.apeiron.math.BigValueCodec.readPacket(in);
            final BigInteger outRate = com.silvia.apeiron.math.BigValueCodec.readPacket(in);
            if (stack == null) continue;
            final FlowRate rate = rates.get(stack);
            if (rate instanceof BigFlowRate) {
                ((BigFlowRate) rate).setBigFlow(inRate, outRate);
            } else {
                final FlowRate replacement = new FlowRate(
                    BigAEStackValues.saturatedLong(inRate),
                    BigAEStackValues.saturatedLong(outRate));
                if (replacement instanceof BigFlowRate) ((BigFlowRate) replacement).setBigFlow(inRate, outRate);
                rates.put(stack, replacement);
            }
        }
    }
}
