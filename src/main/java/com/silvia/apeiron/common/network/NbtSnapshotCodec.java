package com.silvia.apeiron.common.network;

import java.io.IOException;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

import io.netty.buffer.ByteBuf;

/** Snapshot fragments leave room for the enclosing GUI packet within Minecraft's 32 KiB limit. */
public final class NbtSnapshotCodec {

    public static final int FRAGMENT_BYTES = 16 * 1024;
    private static final int MAX_SNAPSHOT_BYTES = 16 * 1024 * 1024;

    private NbtSnapshotCodec() {}

    public static byte[] encode(NBTTagCompound tag) throws IOException {
        byte[] data = CompressedStreamTools.compress(tag);
        if (data.length > MAX_SNAPSHOT_BYTES) throw new IOException("Snapshot is too large");
        return data;
    }

    public static void writeFragment(ByteBuf packet, byte[] data, int offset) {
        if (offset < 0 || offset >= data.length) throw new IllegalArgumentException("Invalid snapshot offset");
        int length = Math.min(FRAGMENT_BYTES, data.length - offset);
        packet.writeInt(data.length);
        packet.writeInt(offset);
        packet.writeShort(length);
        packet.writeBytes(data, offset, length);
    }

    /** Publishes only complete snapshots; a superseding snapshot replaces any incomplete transfer. */
    public static final class Receiver {

        private byte[] pending;
        private int position;

        public NBTTagCompound readFragment(ByteBuf packet) throws IOException {
            if (packet.readableBytes() < 10) throw invalid();
            int total = packet.readInt();
            int offset = packet.readInt();
            int length = packet.readUnsignedShort();
            if (total <= 0 || total > MAX_SNAPSHOT_BYTES
                || offset < 0
                || offset >= total
                || length <= 0
                || length > FRAGMENT_BYTES
                || length > total - offset
                || length > packet.readableBytes()) throw invalid();
            if (offset == 0) {
                pending = new byte[total];
                position = 0;
            }
            if (pending == null || pending.length != total || position != offset) throw invalid();
            packet.readBytes(pending, position, length);
            position += length;
            if (position < total) return null;
            byte[] complete = pending;
            reset();
            return CompressedStreamTools.func_152457_a(complete, new NBTSizeTracker(MAX_SNAPSHOT_BYTES));
        }

        public void reset() {
            pending = null;
            position = 0;
        }

        private IOException invalid() {
            reset();
            return new IOException("Invalid snapshot fragment");
        }
    }
}
