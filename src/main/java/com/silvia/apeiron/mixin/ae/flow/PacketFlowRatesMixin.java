package com.silvia.apeiron.mixin.ae.flow;

import java.io.IOException;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.flow.BigFlowPackets;
import com.silvia.apeiron.ae.flow.BigFlowRate;
import com.silvia.apeiron.ae.sync.BigPacketPayload;

import appeng.api.storage.data.IAEStack;
import appeng.core.sync.packets.PacketFlowRates;
import appeng.me.cache.ItemFlowGridCache.FlowRate;
import io.netty.buffer.ByteBuf;

/** Preserves exact flow totals over the existing AE2 flow-rate packet. */
@Mixin(value = PacketFlowRates.class, remap = false)
public abstract class PacketFlowRatesMixin {

    @Shadow
    @Final
    private Map<IAEStack<?>, FlowRate> rates;

    @Inject(method = "<init>(Lio/netty/buffer/ByteBuf;)V", at = @At("TAIL"))
    private void apeiron$readExact(final ByteBuf stream, final CallbackInfo ci) throws IOException {
        BigFlowPackets.read(stream, this.rates);
    }

    @Inject(method = "<init>(Ljava/util/Map;)V", at = @At("TAIL"))
    private void apeiron$writeExact(final Map<IAEStack<?>, FlowRate> rates, final CallbackInfo ci) {
        if ((Object) this instanceof BigPacketPayload) {
            ((BigPacketPayload) (Object) this).appendApeironFlowRates(rates);
        }
    }
}
