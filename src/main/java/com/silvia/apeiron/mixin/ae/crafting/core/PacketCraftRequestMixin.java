package com.silvia.apeiron.mixin.ae.crafting.core;

import java.io.IOException;
import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.crafting.core.BigCraftRequest;
import com.silvia.apeiron.ae.crafting.packets.BigCraftPackets;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.storage.data.IAEStack;
import appeng.core.sync.packets.PacketCraftRequest;
import io.netty.buffer.ByteBuf;

/** Decodes and applies the optional exact crafting amount on the server. */
@Mixin(value = PacketCraftRequest.class, remap = false)
public abstract class PacketCraftRequestMixin implements BigCraftRequest {

    @Shadow
    private long amount;

    @Unique
    private BigInteger apeiron$amountBig;

    @Inject(method = "<init>(Lio/netty/buffer/ByteBuf;)V", at = @At("TAIL"))
    private void apeiron$readExactAmount(final ByteBuf stream, final CallbackInfo ci) {
        if (stream.readableBytes() < Integer.BYTES + 1) return;
        if (stream.getInt(stream.readerIndex()) != BigCraftPackets.PACKET_MAGIC) return;
        stream.skipBytes(Integer.BYTES);
        try {
            this.apeiron$amountBig = BigValueCodec.readPacket(stream);
        } catch (IOException e) {
            throw new IllegalArgumentException("invalid Apeiron crafting amount", e);
        }
    }

    @Override
    public BigInteger getCraftAmountBig() {
        return this.apeiron$amountBig;
    }

    @Redirect(
        method = "serverPacketData",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IAEStack;setStackSize(J)Lappeng/api/storage/data/IAEStack;"))
    private IAEStack<?> apeiron$setExactCraftAmount(final IAEStack<?> stack, final long legacyAmount) {
        if (this.apeiron$amountBig != null) {
            return BigAEStackValues.set(stack, this.apeiron$amountBig);
        }
        return stack.setStackSize(legacyAmount);
    }
}
