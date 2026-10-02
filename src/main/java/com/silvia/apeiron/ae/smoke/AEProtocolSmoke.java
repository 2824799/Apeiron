package com.silvia.apeiron.ae.smoke;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.crafting.core.BigCraftingCpuEntries;
import com.silvia.apeiron.ae.crafting.core.BigCraftingCpuEntry;
import com.silvia.apeiron.ae.crafting.diagnostics.BigCompletedDiagnosticRecord;
import com.silvia.apeiron.ae.crafting.diagnostics.BigCraftingDiagnosticsValues;
import com.silvia.apeiron.ae.crafting.diagnostics.BigCraftingTimingRecord;
import com.silvia.apeiron.ae.crafting.diagnostics.BigDiagnosticRow;
import com.silvia.apeiron.ae.crafting.diagnostics.BigDiagnosticRowValues;
import com.silvia.apeiron.ae.crafting.packets.BigCraftNotification;
import com.silvia.apeiron.ae.crafting.packets.BigCraftNotificationValues;
import com.silvia.apeiron.ae.crafting.packets.BigCraftPackets;
import com.silvia.apeiron.ae.reshuffle.BigScanRecord;
import com.silvia.apeiron.ae.reshuffle.BigScanRecordValues;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.DimensionalCoord;
import appeng.container.implementations.CraftingCpuEntry;
import appeng.core.sync.AppEngPacketHandlerBase.PacketTypes;
import appeng.core.sync.packets.PacketCraftingCompleteNotification;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.ScanTask;
import appeng.me.cluster.implementations.CraftingCPUCluster.CraftNotification;
import appeng.me.cluster.implementations.CraftingCpuDiagnostics;
import appeng.me.diagnostics.CraftingDiagnosticSessionId;
import appeng.me.diagnostics.DiagnosticRowView;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Exercises packet registration and constructor Mixins that are otherwise first loaded on world join. */
public final class AEProtocolSmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(60).add(BigInteger.valueOf(17));

    private AEProtocolSmoke() {}

    public static void verify() {
        try {
            check(PacketTypes.values().length > 0, "AE packet registry was empty");
            check(new PacketValueConfig("ControllerAnimationDefault", "true").getPacketID()
                == PacketTypes.PACKET_VALUE_CONFIG.ordinal(), "world-join config packet was not registered");
            final IAEItemStack stack = AEItemStack.create(new ItemStack(Items.diamond));
            verifyNotification(stack);
            verifyDiagnostics(stack);
            verifyCpuEntry(stack);
            verifyScanRecord();
        } catch (IOException error) {
            throw new IllegalStateException("AE protocol verification failed", error);
        }
        Apeiron.LOG.info("AE packet registry and constructor big-count runtime verification passed");
    }

    private static void verifyNotification(final IAEItemStack stack) throws IOException {
        BigCraftNotificationValues.capture(HUGE);
        final CraftNotification notification = new CraftNotification(stack, Long.MAX_VALUE, 10L);
        check(((BigCraftNotification) notification).getOutputsCountBig().equals(HUGE),
            "notification constructor lost exact count");
        check(notification.getOutputsCount() == Long.MAX_VALUE, "notification legacy count did not saturate");
        final CraftNotification ordinary = new CraftNotification(stack, 7L, 10L);
        check(((BigCraftNotification) ordinary).getOutputsCountBig().equals(BigInteger.valueOf(7L)),
            "notification constructor reused a previous exact count");

        final NBTTagCompound tag = new NBTTagCompound();
        notification.writeToNBT(tag);
        final CraftNotification restored = new CraftNotification();
        restored.readFromNBT(tag);
        check(((BigCraftNotification) restored).getOutputsCountBig().equals(HUGE),
            "notification NBT lost exact count");

        final PacketCraftingCompleteNotification packet = new PacketCraftingCompleteNotification(notification);
        final ByteBuf payload = packet.getProxy().payload();
        try {
            final ByteBuf reader = payload.duplicate();
            check(reader.readInt() == PacketTypes.PACKET_CRAFTING_COMPLETE_NOTIFICATION.ordinal(),
                "notification packet ID changed");
            final ByteBuf body = reader.duplicate();
            Platform.readStackByte(reader);
            check(reader.readLong() == Long.MAX_VALUE, "notification packet legacy count did not saturate");
            check(reader.readLong() == 10L, "notification packet elapsed time changed");
            check(reader.readInt() == BigCraftPackets.PACKET_MAGIC, "notification exact packet suffix missing");
            check(BigValueCodec.readPacket(reader).equals(HUGE), "notification packet lost exact count");
            new PacketCraftingCompleteNotification(body);
            check(!body.isReadable(), "notification packet constructor did not consume exact suffix");
        } finally {
            payload.release();
        }
    }

    private static void verifyDiagnostics(final IAEItemStack stack) {
        final CraftingDiagnosticSessionId session = CraftingDiagnosticSessionId.of(1L);
        BigCraftingDiagnosticsValues.captureTiming(HUGE);
        final CraftingCpuDiagnostics.CraftingTimingRecord timing =
            new CraftingCpuDiagnostics.CraftingTimingRecord(Long.MAX_VALUE, 1L, session);
        check(BigCraftingTimingRecord.remaining(timing).equals(HUGE), "timing constructor lost remaining count");
        check(BigCraftingTimingRecord.original(timing).equals(HUGE), "timing constructor lost original count");
        check(timing.getRemainingToProduce() == Long.MAX_VALUE, "timing legacy count did not saturate");
        check(BigCraftingTimingRecord.remaining(new CraftingCpuDiagnostics.CraftingTimingRecord(7L, 1L, session))
            .equals(BigInteger.valueOf(7L)), "timing constructor reused a previous exact count");

        BigCraftingDiagnosticsValues.captureCompleted(HUGE);
        final CraftingCpuDiagnostics.CompletedDiagnosticRecord completed =
            new CraftingCpuDiagnostics.CompletedDiagnosticRecord(stack, session, Long.MAX_VALUE, 1L, 11L, 10L);
        check(BigCompletedDiagnosticRecord.produced(completed).equals(HUGE),
            "completed diagnostic constructor lost exact count");
        check(completed.getProducedAmount() == Long.MAX_VALUE, "completed diagnostic legacy count did not saturate");
        check(BigCompletedDiagnosticRecord.produced(
            new CraftingCpuDiagnostics.CompletedDiagnosticRecord(stack, session, 7L, 1L, 11L, 10L))
            .equals(BigInteger.valueOf(7L)), "completed diagnostic constructor reused a previous exact count");

        BigDiagnosticRowValues.capture(HUGE, HUGE.add(BigInteger.ONE), HUGE.add(BigInteger.TEN));
        final DiagnosticRowView row = new DiagnosticRowView(stack, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);
        final BigDiagnosticRow exact = (BigDiagnosticRow) (Object) row;
        check(exact.getTotalProducedBig().equals(HUGE), "diagnostic row lost production count");
        check(exact.getElapsedTimeTicksBig().equals(HUGE.add(BigInteger.ONE)), "diagnostic row lost elapsed ticks");
        check(exact.getSampleCountBig().equals(HUGE.add(BigInteger.TEN)), "diagnostic row lost sample count");
        check(((BigDiagnosticRow) (Object) new DiagnosticRowView(stack, 7L, 8L, 9L)).getTotalProducedBig()
            .equals(BigInteger.valueOf(7L)), "diagnostic row constructor reused previous exact values");
    }

    private static void verifyCpuEntry(final IAEItemStack stack) throws IOException {
        BigCraftingCpuEntries.capture(HUGE, HUGE.add(BigInteger.ONE), HUGE.add(BigInteger.TEN));
        final CraftingCpuEntry entry = new CraftingCpuEntry(stack, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, null);
        check(BigCraftingCpuEntry.stored(entry).equals(HUGE), "CPU entry constructor lost stored count");
        check(BigCraftingCpuEntry.active(entry).equals(HUGE.add(BigInteger.ONE)), "CPU entry constructor lost active count");
        check(BigCraftingCpuEntry.pending(entry).equals(HUGE.add(BigInteger.TEN)), "CPU entry constructor lost pending count");
        check(entry.getTotalAmount() == Long.MAX_VALUE, "CPU entry legacy total did not saturate");

        final ByteBuf buffer = Unpooled.buffer();
        try {
            entry.writeToPacket(buffer);
            final CraftingCpuEntry restored = new CraftingCpuEntry(buffer);
            check(BigCraftingCpuEntry.stored(restored).equals(HUGE), "CPU entry packet lost exact count");
            check(!buffer.isReadable(), "CPU entry packet constructor left unread bytes");
        } finally {
            buffer.release();
        }
        check(BigCraftingCpuEntry.stored(new CraftingCpuEntry(stack, 7L, 8L, 9L, null))
            .equals(BigInteger.valueOf(7L)), "CPU entry constructor reused previous exact values");
    }

    private static void verifyScanRecord() {
        final ItemStack cell = new ItemStack(Items.diamond);
        final DimensionalCoord coordinate = new DimensionalCoord(1, 2, 3, 0);
        BigScanRecordValues.capture(BigInteger.ONE, BigInteger.TEN, HUGE, HUGE.add(BigInteger.TEN));
        try {
            final ScanTask.ScanRecord record = new ScanTask.ScanRecord(
                0, cell, 1L, 10L, Long.MAX_VALUE, Long.MAX_VALUE, coordinate, Collections.emptyList());
            check(((BigScanRecord) record).getBytesUsedBig().equals(HUGE), "scan constructor lost used bytes");
            check(((BigScanRecord) record).getBytesTotalBig().equals(HUGE.add(BigInteger.TEN)),
                "scan constructor lost total bytes");
        } finally {
            BigScanRecordValues.clear();
        }
        final ScanTask.ScanRecord ordinary = new ScanTask.ScanRecord(
            0, cell, 1L, 10L, 7L, 8L, coordinate, Collections.emptyList());
        check(((BigScanRecord) ordinary).getBytesUsedBig().equals(BigInteger.valueOf(7L)),
            "scan constructor reused previous exact values");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
