package com.silvia.apeiron.common.machine.me.input.storage;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;

/** Only the open contents window routes player inventory Shift clicks into its selected buffer. */
public final class PatternBufferQuickMove {

    private static final Map<Container, Session> SESSIONS = new WeakHashMap<>();

    private PatternBufferQuickMove() {}

    public static void open(Container container, MTEInfinitePatternInputAssembly machine, IntSupplier page,
        BooleanSupplier accessible) {
        SESSIONS.put(container, new Session(machine, page, accessible));
    }

    public static void close(Container container) {
        SESSIONS.remove(container);
    }

    public static boolean handles(Container container, Slot slot, EntityPlayer player) {
        boolean playerSlot = slot.inventory == player.inventory
            || com.cleanroommc.modularui.widgets.slot.ModularSlot.isPlayerSlot(slot)
                && com.cleanroommc.modularui.widgets.slot.ModularSlot.getPlayerSlotPlayer(slot) == player;
        return SESSIONS.containsKey(container) && playerSlot && slot.getSlotIndex() < 36;
    }

    public static ItemStack transfer(Container container, Slot slot, EntityPlayer player) {
        Session session = SESSIONS.get(container);
        if (session == null || !session.accessible.getAsBoolean() || player.worldObj.isRemote) return null;
        ItemStack input = slot.getStack();
        if (input == null || input.stackSize <= 0) return null;
        ItemStack before = input.copy();
        if (!PatternBufferTransfers.depositInventory(session.machine, player, session.page.getAsInt(), slot))
            return null;
        slot.onSlotChanged();
        return before;
    }

    private static final class Session {

        final MTEInfinitePatternInputAssembly machine;
        final IntSupplier page;
        final BooleanSupplier accessible;

        Session(MTEInfinitePatternInputAssembly machine, IntSupplier page, BooleanSupplier accessible) {
            this.machine = machine;
            this.page = page;
            this.accessible = accessible;
        }
    }
}
