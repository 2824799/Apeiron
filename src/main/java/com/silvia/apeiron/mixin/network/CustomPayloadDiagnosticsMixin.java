package com.silvia.apeiron.mixin.network;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.cleanroommc.modularui.network.NetworkUtils;

import io.netty.buffer.Unpooled;

/** Vanilla's exception omits the channel, making unrelated oversized mod packets indistinguishable. */
@Mixin(C17PacketCustomPayload.class)
public abstract class CustomPayloadDiagnosticsMixin {

    @ModifyConstant(
        method = "<init>(Ljava/lang/String;[B)V",
        constant = @Constant(stringValue = "Payload may not be larger than 32k"),
        require = 1)
    private String apeiron$identifyOversizedPacket(String message, String channel, byte[] data) {
        return message + " (channel=" + channel + ", bytes=" + data.length + apeiron$packetDetails(channel, data) + ")";
    }

    @Unique
    private static String apeiron$packetDetails(String channel, byte[] data) {
        if (!"modularui2".equals(channel) || data.length == 0) return "";
        int discriminator = data[0] & 255;
        if (discriminator != 11) return ", type=" + discriminator;
        PacketBuffer packet = new PacketBuffer(Unpooled.wrappedBuffer(data));
        try {
            packet.readUnsignedByte();
            int network = packet.readVarIntFromBuffer();
            String panel = NetworkUtils.readStringSafe(packet);
            String key = NetworkUtils.readStringSafe(packet);
            return ", type=PacketSyncHandler, network=" + network + ", panel=" + panel + ", key=" + key;
        } catch (RuntimeException malformed) {
            return ", type=" + discriminator;
        } finally {
            packet.release();
        }
    }
}
