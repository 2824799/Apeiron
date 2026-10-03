package com.silvia.apeiron.client.gui.sync;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import com.gtnewhorizons.modularui.api.math.Size;
import com.gtnewhorizons.modularui.common.widget.SyncedWidget;
import com.silvia.apeiron.common.network.NbtSnapshotCodec;

/** The legacy ModularUI counterpart of ChunkedNbtSyncValue. */
public final class ChunkedNbtSyncWidget extends SyncedWidget {

    private final Supplier<NBTTagCompound> getter;
    private final Consumer<NBTTagCompound> setter;
    private final NbtSnapshotCodec.Receiver receiver = new NbtSnapshotCodec.Receiver();
    private NBTTagCompound previous;

    public ChunkedNbtSyncWidget(Supplier<NBTTagCompound> getter, Consumer<NBTTagCompound> setter) {
        this.getter = getter;
        this.setter = setter;
        setSynced(true, false);
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        if (isClient()) return;
        NBTTagCompound current = getter.get();
        if (!init && current.equals(previous)) return;
        try {
            byte[] data = NbtSnapshotCodec.encode(current);
            for (int offset = 0; offset < data.length; offset += NbtSnapshotCodec.FRAGMENT_BYTES) {
                final int start = offset;
                syncToClient(0, packet -> NbtSnapshotCodec.writeFragment(packet, data, start));
            }
            previous = (NBTTagCompound) current.copy();
            markForUpdate();
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot synchronize NBT snapshot", failure);
        }
    }

    @Override
    public void readOnClient(int id, PacketBuffer packet) throws IOException {
        if (id != 0) return;
        NBTTagCompound complete = receiver.readFragment(packet);
        if (complete != null) setter.accept(complete);
    }

    @Override
    public void readOnServer(int id, PacketBuffer packet) {}

    @Override
    protected Size determineSize(int maxWidth, int maxHeight) {
        return Size.ZERO;
    }

    @Override
    public void onDestroy() {
        receiver.reset();
        super.onDestroy();
    }
}
