package com.silvia.apeiron.mixin.ae.crafting.core;

import java.io.IOException;
import java.math.BigInteger;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.crafting.packets.BigCraftNotification;
import com.silvia.apeiron.ae.sync.BigPacketPayload;
import com.silvia.apeiron.math.BigNumberFormatter;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.core.sync.packets.PacketCraftingCompleteNotification;
import appeng.me.cluster.implementations.CraftingCPUCluster.CraftNotification;
import io.netty.buffer.ByteBuf;

/** Preserves exact completion counts in AE's notification packet and client text. */
@Mixin(value = PacketCraftingCompleteNotification.class, remap = false)
public abstract class PacketCraftingCompleteNotificationBigMixin {

    @Shadow
    @Final
    private long numsOfOutput;

    @Unique
    private BigInteger apeiron$numsOfOutput;

    @Inject(method = "<init>(Lio/netty/buffer/ByteBuf;)V", at = @At("TAIL"))
    private void apeiron$readExact(final ByteBuf stream, final CallbackInfo ci) throws IOException {
        if (stream.readableBytes() < Integer.BYTES + 1) return;
        if (stream.getInt(stream.readerIndex()) != com.silvia.apeiron.ae.crafting.packets.BigCraftPackets.PACKET_MAGIC)
            return;
        stream.skipBytes(Integer.BYTES);
        this.apeiron$numsOfOutput = BigValueCodec.readPacket(stream);
    }

    @Inject(
        method = "<init>(Lappeng/me/cluster/implementations/CraftingCPUCluster$CraftNotification;)V",
        at = @At("RETURN"))
    private void apeiron$writeExact(final CraftNotification notification, final CallbackInfo ci) {
        if (notification instanceof BigCraftNotification exact && (Object) this instanceof BigPacketPayload) {
            this.apeiron$numsOfOutput = exact.getOutputsCountBig();
            ((BigPacketPayload) (Object) this).appendApeironBigInteger(this.apeiron$numsOfOutput);
        }
    }

    @Redirect(
        method = "clientPacketData",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/StatCollector;translateToLocalFormatted(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;",
            remap = true))
    private String apeiron$format(final String key, final Object[] args) {
        if (args != null && args.length > 0) {
            final Object[] copy = args.clone();
            copy[0] = this.apeiron$numsOfOutput == null ? Long.valueOf(this.numsOfOutput)
                : BigNumberFormatter.formatExact(this.apeiron$numsOfOutput);
            return net.minecraft.util.StatCollector.translateToLocalFormatted(key, copy);
        }
        return net.minecraft.util.StatCollector.translateToLocalFormatted(key, args);
    }
}
