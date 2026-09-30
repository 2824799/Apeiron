package com.silvia.apeiron.mixin.ae.crafting.core;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.crafting.diagnostics.BigDiagnosticRow;
import com.silvia.apeiron.ae.sync.BigPacketPayload;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.core.sync.packets.PacketCraftingDiagnosticsUpdate;
import appeng.me.diagnostics.DiagnosticRowView;
import io.netty.buffer.ByteBuf;

/** Reads and writes the optional exact values appended to AE's diagnostics packet. */
@Mixin(value = PacketCraftingDiagnosticsUpdate.class, remap = false)
public abstract class PacketCraftingDiagnosticsUpdateBigMixin {

    private static final int APEIRON_DIAGNOSTIC_MAGIC = 0x41504447;

    @Shadow
    @Final
    private List<DiagnosticRowView> rows;

    @Inject(method = "<init>(Lio/netty/buffer/ByteBuf;)V", at = @At("TAIL"))
    private void apeiron$readExact(final ByteBuf stream, final CallbackInfo ci) throws IOException {
        if (stream.readableBytes() < Integer.BYTES * 2
            || stream.getInt(stream.readerIndex()) != APEIRON_DIAGNOSTIC_MAGIC) return;
        stream.skipBytes(Integer.BYTES);
        final int count = stream.readInt();
        if (count != this.rows.size()) throw new IOException("diagnostics row count mismatch");
        for (final DiagnosticRowView row : this.rows) {
            final BigInteger total = BigValueCodec.readPacket(stream);
            final BigInteger elapsed = BigValueCodec.readPacket(stream);
            final BigInteger samples = BigValueCodec.readPacket(stream);
            if ((Object) row instanceof BigDiagnosticRow exact) exact.setExactValues(total, elapsed, samples);
        }
    }

    @Inject(method = "<init>(Ljava/util/List;)V", at = @At("TAIL"))
    private void apeiron$writeExact(final List<DiagnosticRowView> rows, final CallbackInfo ci) {
        if ((Object) this instanceof BigPacketPayload) {
            ((BigPacketPayload) (Object) this).appendApeironDiagnosticRows(rows);
        }
    }
}
