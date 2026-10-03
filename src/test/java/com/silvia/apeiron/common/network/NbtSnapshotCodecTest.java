package com.silvia.apeiron.common.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Random;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class NbtSnapshotCodecTest {

    private NBTTagCompound fullSnapshot() {
        Random random = new Random(7);
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
        NBTTagCompound snapshot = new NBTTagCompound();
        snapshot.setTag("slots", slots);
        return snapshot;
    }

    @Test
    public void full360SlotSnapshotFitsEveryFragmentAndPreservesAllData() throws IOException {
        NBTTagCompound original = fullSnapshot();
        byte[] data = NbtSnapshotCodec.encode(original);
        assertTrue("fixture must exceed the old single-packet limit", data.length > 32767);
        NbtSnapshotCodec.Receiver receiver = new NbtSnapshotCodec.Receiver();
        for (int offset = 0; offset < data.length; offset += NbtSnapshotCodec.FRAGMENT_BYTES) {
            ByteBuf fragment = Unpooled.buffer();
            try {
                NbtSnapshotCodec.writeFragment(fragment, data, offset);
                assertTrue("fragment leaves no room for the GUI envelope", fragment.readableBytes() + 2048 < 32767);
                NBTTagCompound received = receiver.readFragment(fragment);
                if (offset + NbtSnapshotCodec.FRAGMENT_BYTES >= data.length) assertEquals(original, received);
                else assertNull("partial snapshot must not be published", received);
            } finally {
                fragment.release();
            }
        }
    }

    @Test
    public void newSnapshotReplacesAnInterruptedTransferIncludingEmptySlots() throws IOException {
        byte[] old = NbtSnapshotCodec.encode(fullSnapshot());
        NbtSnapshotCodec.Receiver receiver = new NbtSnapshotCodec.Receiver();
        ByteBuf first = Unpooled.buffer();
        ByteBuf next = Unpooled.buffer();
        try {
            NbtSnapshotCodec.writeFragment(first, old, 0);
            assertNull(receiver.readFragment(first));
            NBTTagCompound empty = new NBTTagCompound();
            empty.setTag("slots", new NBTTagList());
            NbtSnapshotCodec.writeFragment(next, NbtSnapshotCodec.encode(empty), 0);
            assertEquals(empty, receiver.readFragment(next));
        } finally {
            first.release();
            next.release();
        }
    }

    @Test
    public void rejectsTruncatedOrOutOfOrderFragmentsAndRecovers() throws IOException {
        byte[] data = NbtSnapshotCodec.encode(fullSnapshot());
        NbtSnapshotCodec.Receiver receiver = new NbtSnapshotCodec.Receiver();
        ByteBuf invalid = Unpooled.buffer();
        try {
            NbtSnapshotCodec.writeFragment(invalid, data, NbtSnapshotCodec.FRAGMENT_BYTES);
            expectInvalid(receiver, invalid);
            invalid.clear();
            invalid.writeInt(100);
            invalid.writeInt(0);
            invalid.writeShort(50);
            invalid.writeByte(1);
            expectInvalid(receiver, invalid);
            invalid.clear();
            invalid.writeInt(Integer.MAX_VALUE);
            invalid.writeInt(0);
            invalid.writeShort(1);
            invalid.writeByte(1);
            expectInvalid(receiver, invalid);
            invalid.clear();
            NBTTagCompound replacement = new NBTTagCompound();
            replacement.setString("value", "recovered");
            NbtSnapshotCodec.writeFragment(invalid, NbtSnapshotCodec.encode(replacement), 0);
            assertEquals(replacement, receiver.readFragment(invalid));
        } finally {
            invalid.release();
        }
    }

    private void expectInvalid(NbtSnapshotCodec.Receiver receiver, ByteBuf packet) throws IOException {
        try {
            receiver.readFragment(packet);
            fail("malformed fragment was accepted");
        } catch (IOException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("fragment"));
        }
    }
}
