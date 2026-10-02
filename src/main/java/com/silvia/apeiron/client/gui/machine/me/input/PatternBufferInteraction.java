package com.silvia.apeiron.client.gui.machine.me.input;

import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.input.storage.BigPatternBuffer;
import com.silvia.apeiron.common.machine.me.input.storage.PatternBufferTransfers;

import appeng.api.storage.data.IAEStack;
import appeng.util.Platform;

/** Send the displayed type and page, so stale clicks cannot act on newly compacted contents. */
final class PatternBufferInteraction extends SyncHandler<PatternBufferInteraction> {

    private final MTEInfinitePatternInputAssembly machine;
    private final BigPatternBuffer preview;
    private final IntSupplier page;
    private final BooleanSupplier accessible;
    private final int slot;
    private final boolean fluid;

    PatternBufferInteraction(MTEInfinitePatternInputAssembly machine, BigPatternBuffer preview, IntSupplier page,
        BooleanSupplier accessible, int slot, boolean fluid) {
        this.machine = machine;
        this.preview = preview;
        this.page = page;
        this.accessible = accessible;
        this.slot = slot;
        this.fluid = fluid;
        allowC2S();
    }

    boolean click(int button) {
        if (button < 0 || button > 1) return false;
        IAEStack<?> shown = PatternBufferTransfers.stackAt(preview, slot, fluid);
        syncToServer(0, packet -> {
            MouseData.create(button)
                .writeToPacket(packet);
            packet.writeInt(page.getAsInt());
            packet.writeBoolean(shown != null);
            if (shown != null) Platform.writeStackByte(shown, packet);
        });
        return true;
    }

    @Override
    public void readOnClient(int id, PacketBuffer packet) {}

    @Override
    public void readOnServer(int id, PacketBuffer packet) throws IOException {
        if (id != 0) return;
        MouseData mouse = MouseData.readPacket(packet);
        int expectedPage = packet.readInt();
        IAEStack<?> expected = packet.readBoolean() ? Platform.readStackByte(packet) : null;
        if (mouse.isClient() || !accessible.getAsBoolean() || expectedPage != page.getAsInt()) return;
        PatternBufferTransfers.click(
            machine,
            getSyncManager().getPlayer(),
            expectedPage,
            slot,
            fluid,
            expected,
            mouse.mouseButton,
            mouse.shift);
    }
}
