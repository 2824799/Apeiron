package com.silvia.apeiron.mixin.ae.stack;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.silvia.apeiron.ae.crafting.diagnostics.BigDiagnosticRow;
import com.silvia.apeiron.ae.crafting.packets.BigCraftPackets;
import com.silvia.apeiron.ae.flow.BigFlowPackets;
import com.silvia.apeiron.ae.sync.BigPacketPayload;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.storage.data.IAEStack;
import appeng.core.sync.AppEngPacket;
import appeng.me.cache.ItemFlowGridCache.FlowRate;
import appeng.me.diagnostics.DiagnosticRowView;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Gives compatible AE packets an optional exact-integer suffix. */
@Mixin(value = AppEngPacket.class, remap = false)
public abstract class AppEngPacketMixin implements BigPacketPayload {

    private static final int APEIRON_DIAGNOSTIC_MAGIC = 0x41504447;

    @Shadow
    private ByteBuf p;

    @Shadow
    protected abstract void configureWrite(ByteBuf data);

    @Override
    public void appendApeironBigInteger(final BigInteger value) {
        if (this.p == null || value == null) return;

        final ByteBuf old = this.p;
        final ByteBuf replacement = Unpooled.buffer(old.readableBytes() + 16 + value.toByteArray().length);
        replacement.writeBytes(old, old.readerIndex(), old.readableBytes());
        replacement.writeInt(BigCraftPackets.PACKET_MAGIC);
        BigValueCodec.writePacket(replacement, value);
        this.configureWrite(replacement);
    }

    @Override
    public void appendApeironFlowRates(final Map<IAEStack<?>, FlowRate> rates) {
        if (this.p == null || rates == null || !BigFlowPackets.hasBigValues(rates)) return;
        final ByteBuf old = this.p;
        final ByteBuf replacement = Unpooled.buffer(old.readableBytes() + rates.size() * 64);
        replacement.writeBytes(old, old.readerIndex(), old.readableBytes());
        BigFlowPackets.write(replacement, rates);
        this.configureWrite(replacement);
    }

    @Override
    public void appendApeironDiagnosticRows(final List<DiagnosticRowView> rows) {
        if (this.p == null || rows == null) return;
        final int extra = 8 + rows.size() * 48;
        final ByteBuf old = this.p;
        final ByteBuf replacement = Unpooled.buffer(old.readableBytes() + extra);
        replacement.writeBytes(old, old.readerIndex(), old.readableBytes());
        replacement.writeInt(APEIRON_DIAGNOSTIC_MAGIC);
        replacement.writeInt(rows.size());
        for (final DiagnosticRowView row : rows) {
            final BigDiagnosticRow exact = (Object) row instanceof BigDiagnosticRow ? (BigDiagnosticRow) (Object) row
                : null;
            BigValueCodec.writePacket(
                replacement,
                exact == null ? BigInteger.valueOf(row.totalProduced) : exact.getTotalProducedBig());
            BigValueCodec.writePacket(
                replacement,
                exact == null ? BigInteger.valueOf(row.elapsedTimeTicks) : exact.getElapsedTimeTicksBig());
            BigValueCodec.writePacket(
                replacement,
                exact == null ? BigInteger.valueOf(row.sampleCount) : exact.getSampleCountBig());
        }
        this.configureWrite(replacement);
    }
}
