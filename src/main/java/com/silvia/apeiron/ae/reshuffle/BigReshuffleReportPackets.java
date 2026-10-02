package com.silvia.apeiron.ae.reshuffle;

import java.io.IOException;
import java.math.BigInteger;

import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.helpers.ReshuffleReport;
import appeng.helpers.ReshuffleReport.ItemChange;
import appeng.util.Platform;
import io.netty.buffer.ByteBuf;

/** Optional exact suffix for AE's legacy storage-reshuffle report packet. */
public final class BigReshuffleReportPackets {

    public static final int PACKET_MAGIC = 0x41505252;

    private BigReshuffleReportPackets() {}

    public static void write(final ByteBuf out, final ReshuffleReport report) {
        if (!(report instanceof BigReshuffleReportAccess access)) return;
        final BigInteger extracted = access.getExtractedItemsBig();
        final BigInteger injected = access.getInjectedItemsBig();
        final BigInteger before = access.getBeforeItemsBig();
        final BigInteger after = access.getAfterItemsBig();
        if (!BigAEStackValues.isBigValue(extracted) && !BigAEStackValues.isBigValue(injected)
            && !BigAEStackValues.isBigValue(before)
            && !BigAEStackValues.isBigValue(after)
            && !hasBigChanges(report)) return;

        out.writeInt(PACKET_MAGIC);
        com.silvia.apeiron.math.BigValueCodec.writePacket(out, extracted);
        com.silvia.apeiron.math.BigValueCodec.writePacket(out, injected);
        com.silvia.apeiron.math.BigValueCodec.writePacket(out, before);
        com.silvia.apeiron.math.BigValueCodec.writePacket(out, after);
        writeChanges(out, report.gainedItems);
        writeChanges(out, report.lostItems);
    }

    public static void read(final ByteBuf in, final ReshuffleReport report) throws IOException {
        if (in.readableBytes() < Integer.BYTES || in.getInt(in.readerIndex()) != PACKET_MAGIC) return;
        in.skipBytes(Integer.BYTES);
        if (!(report instanceof BigReshuffleReportAccess reportAccess)) {
            throw new IOException("Apeiron reshuffle report access is unavailable");
        }
        final BigInteger extracted = com.silvia.apeiron.math.BigValueCodec.readPacket(in);
        final BigInteger injected = com.silvia.apeiron.math.BigValueCodec.readPacket(in);
        final BigInteger before = com.silvia.apeiron.math.BigValueCodec.readPacket(in);
        final BigInteger after = com.silvia.apeiron.math.BigValueCodec.readPacket(in);
        reportAccess.setTransferTotalsBig(extracted, injected);
        if (report.phase != appeng.api.config.ReshufflePhase.DONE) return;

        report.gainedItems.clear();
        report.lostItems.clear();
        readChanges(in, report.gainedItems);
        readChanges(in, report.lostItems);
    }

    private static boolean hasBigChanges(final ReshuffleReport report) {
        for (final ItemChange change : report.gainedItems) {
            if (change instanceof BigReshuffleItemChangeAccess access
                && BigAEStackValues.isBigValue(access.getBeforeCountBig())) return true;
            if (change instanceof BigReshuffleItemChangeAccess access
                && BigAEStackValues.isBigValue(access.getAfterCountBig())) return true;
        }
        for (final ItemChange change : report.lostItems) {
            if (change instanceof BigReshuffleItemChangeAccess access
                && BigAEStackValues.isBigValue(access.getBeforeCountBig())) return true;
            if (change instanceof BigReshuffleItemChangeAccess access
                && BigAEStackValues.isBigValue(access.getAfterCountBig())) return true;
        }
        return false;
    }

    private static void writeChanges(final ByteBuf out, final java.util.List<ItemChange> changes) {
        out.writeInt(changes.size());
        for (final ItemChange change : changes) {
            Platform.writeStackByte(change.stack, out);
            final BigReshuffleItemChangeAccess access = (BigReshuffleItemChangeAccess) change;
            com.silvia.apeiron.math.BigValueCodec.writePacket(out, access.getBeforeCountBig());
            com.silvia.apeiron.math.BigValueCodec.writePacket(out, access.getAfterCountBig());
        }
    }

    private static void readChanges(final ByteBuf in, final java.util.List<ItemChange> changes) throws IOException {
        if (in.readableBytes() < Integer.BYTES) throw new IOException("missing reshuffle change count");
        final int count = in.readInt();
        if (count < 0 || count > 1_000_000) throw new IOException("invalid reshuffle change count: " + count);
        for (int i = 0; i < count; i++) {
            final appeng.api.storage.data.IAEStack<?> stack = Platform.readStackByte(in);
            final BigInteger before = com.silvia.apeiron.math.BigValueCodec.readPacket(in);
            final BigInteger after = com.silvia.apeiron.math.BigValueCodec.readPacket(in);
            final ItemChange change = new ItemChange(
                stack,
                BigAEStackValues.saturatedLong(before),
                BigAEStackValues.saturatedLong(after));
            ((BigReshuffleItemChangeAccess) change).setCountsBig(before, after);
            changes.add(change);
        }
    }
}
