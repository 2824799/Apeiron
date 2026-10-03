package com.silvia.apeiron.common.network.verification;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;

import com.cleanroommc.modularui.network.packets.PacketSyncHandler;
import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.client.gui.sync.ChunkedNbtSyncValue;
import com.silvia.apeiron.client.gui.sync.ChunkedNbtSyncWidget;
import com.silvia.apeiron.common.network.NbtSnapshotCodec;

import cpw.mods.fml.common.FMLCommonHandler;
import io.netty.buffer.Unpooled;

/** Exercises actual ModularUI envelopes and both receiver callbacks after Forge has initialized. */
public final class GuiSnapshotSmoke {

    private GuiSnapshotSmoke() {}

    public static void verify() {
        com.silvia.apeiron.common.machine.energy.verification.MachineDisplaySmoke.verify();
        try {
            Random random = new Random(17);
            NBTTagList slots = new NBTTagList();
            for (int slot = 0; slot < 360; slot++) {
                NBTTagCompound cell = new NBTTagCompound();
                cell.setInteger("slot", slot);
                byte[] itemData = new byte[1024];
                random.nextBytes(itemData);
                cell.setByteArray("tag", itemData);
                cell.setString("count", "100000000000000000000000000000000000000000000000001");
                cell.setBoolean("infinite", slot % 2 == 0);
                slots.appendTag(cell);
            }
            NBTTagCompound original = new NBTTagCompound();
            original.setTag("slots", slots);
            byte[] data = NbtSnapshotCodec.encode(original);
            check(data.length > 32767, "fixture did not exceed the legacy packet limit");
            AtomicReference<NBTTagCompound> modernResult = new AtomicReference<>();
            AtomicReference<NBTTagCompound> legacyResult = new AtomicReference<>();
            AtomicInteger updates = new AtomicInteger();
            ChunkedNbtSyncValue modern = new ChunkedNbtSyncValue(NBTTagCompound::new, modernResult::set);
            modern.setChangeListener(updates::incrementAndGet);
            ChunkedNbtSyncWidget legacy = new ChunkedNbtSyncWidget(NBTTagCompound::new, legacyResult::set);
            check(!modern.isAllowC2S() && !legacy.syncsToServer(), "display data accepted client writes");
            for (int offset = 0; offset < data.length; offset += NbtSnapshotCodec.FRAGMENT_BYTES) {
                PacketBuffer fragment = new PacketBuffer(Unpooled.buffer());
                PacketBuffer envelope = new PacketBuffer(Unpooled.buffer());
                PacketSyncHandler decoded = new PacketSyncHandler();
                try {
                    fragment.writeVarIntToBuffer(0);
                    NbtSnapshotCodec.writeFragment(fragment, data, offset);
                    new PacketSyncHandler(1, "apeiron_stocking", "apeiron_stocking_snapshot", false, fragment)
                        .write(envelope);
                    byte[] payload = new byte[envelope.readableBytes()];
                    envelope.getBytes(envelope.readerIndex(), payload);
                    check(payload.length < 32767, "ModularUI packet exceeded the legacy payload limit");
                    if (FMLCommonHandler.instance()
                        .getSide()
                        .isClient()) {
                        new C17PacketCustomPayload("modularui2", payload);
                    }
                    decoded.read(envelope);
                    int id = decoded.packet.readVarIntFromBuffer();
                    decoded.packet.markReaderIndex();
                    modern.readOnClient(id, decoded.packet);
                    decoded.packet.resetReaderIndex();
                    legacy.readOnClient(id, decoded.packet);
                } finally {
                    if (decoded.packet != null) decoded.packet.release();
                    fragment.release();
                    envelope.release();
                }
            }
            check(original.equals(modernResult.get()) && original.equals(legacyResult.get()), "snapshot lost data");
            check(updates.get() == 1, "partial snapshots triggered GUI rebuilds");
            modern.dispose();
            if (FMLCommonHandler.instance()
                .getSide()
                .isClient()) {
                try {
                    new C17PacketCustomPayload("apeiron_verify", new byte[32767]);
                    throw new IllegalStateException("Oversized custom payload was accepted");
                } catch (IllegalArgumentException expected) {
                    check(
                        expected.getMessage()
                            .contains("channel=apeiron_verify")
                            && expected.getMessage()
                                .contains("bytes=32767"),
                        "oversized payload diagnostics did not identify the channel and size");
                }
                Apeiron.LOG.info("Custom payload diagnostic verification passed: channel and byte count preserved");
            }
            Apeiron.LOG.info(
                "GUI snapshot verification passed: {} bytes, 360 slots, bounded ModularUI packets and both UI receivers",
                data.length);
        } catch (IOException failure) {
            throw new IllegalStateException("GUI snapshot verification failed", failure);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("GUI snapshot verification: " + message);
    }
}
