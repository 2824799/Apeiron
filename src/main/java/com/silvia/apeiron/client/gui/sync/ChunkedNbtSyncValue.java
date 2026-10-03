package com.silvia.apeiron.client.gui.sync;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.utils.serialization.ByteBufAdapters;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.silvia.apeiron.common.network.NbtSnapshotCodec;

/** Display snapshots travel only from server to client, in fragments bounded by their encoded byte size. */
public final class ChunkedNbtSyncValue extends GenericSyncValue<NBTTagCompound, ChunkedNbtSyncValue> {

    private final NbtSnapshotCodec.Receiver receiver = new NbtSnapshotCodec.Receiver();

    public ChunkedNbtSyncValue(Supplier<NBTTagCompound> getter, Consumer<NBTTagCompound> setter) {
        super(NBTTagCompound.class, getter, setter, ByteBufAdapters.NBT, tag -> (NBTTagCompound) tag.copy());
    }

    @Override
    protected void sync() {
        if (getSyncManager().isClient()) return;
        try {
            byte[] data = NbtSnapshotCodec.encode(getValue());
            for (int offset = 0; offset < data.length; offset += NbtSnapshotCodec.FRAGMENT_BYTES) {
                final int start = offset;
                syncToClient(SYNC_VALUE, packet -> NbtSnapshotCodec.writeFragment(packet, data, start));
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot synchronize NBT snapshot", failure);
        }
    }

    @Override
    public void readOnClient(int id, PacketBuffer packet) throws IOException {
        if (id != SYNC_VALUE) return;
        NBTTagCompound complete = receiver.readFragment(packet);
        if (complete != null) setValue(complete, true, false);
    }

    @Override
    public void readOnServer(int id, PacketBuffer packet) {}

    @Override
    public void dispose() {
        receiver.reset();
        super.dispose();
    }
}
