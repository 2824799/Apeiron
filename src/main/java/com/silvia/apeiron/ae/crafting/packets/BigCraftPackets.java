package com.silvia.apeiron.ae.crafting.packets;

import java.math.BigInteger;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.sync.BigPacketPayload;

import appeng.core.sync.AppEngPacket;
import appeng.core.sync.packets.PacketCraftRequest;

/** Adds an optional exact crafting amount without changing AE2's packet id or legacy fields. */
public final class BigCraftPackets {

    public static final int PACKET_MAGIC = 0x41504352;

    private BigCraftPackets() {}

    public static AppEngPacket withExactAmount(final AppEngPacket packet, final BigInteger amount) {
        if (!(packet instanceof PacketCraftRequest) || amount == null || !BigAEStackValues.isBigValue(amount)) {
            return packet;
        }
        if (packet instanceof BigPacketPayload) {
            ((BigPacketPayload) packet).appendApeironBigInteger(amount);
        }
        return packet;
    }
}
