package com.silvia.apeiron.common.machine.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;

import com.silvia.apeiron.api.machine.me.input.BigDualInputHatch;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.input.storage.BigPatternBuffer;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.tileentities.machines.IDualInputHatch;

/** Temporary native slots over owned pattern materials; only detached copies may have their NBT changed. */
public final class MutablePatternItemInputs implements AutoCloseable {

    private final ArrayList<MTEHatchInputBus> buses;
    private final List<Slot> slots = new ArrayList<>();
    private final Set<ItemStack> owned = Collections.newSetFromMap(new IdentityHashMap<>());

    public MutablePatternItemInputs(MTEMultiBlockBase machine) {
        buses = new ArrayList<>(machine.mInputBusses);
        Set<MTEInfinitePatternInputAssembly> sources = Collections.newSetFromMap(new IdentityHashMap<>());
        for (IDualInputHatch hatch : machine.mDualInputHatches) {
            if (!(hatch instanceof BigDualInputHatch)) continue;
            MTEInfinitePatternInputAssembly source = ((BigDualInputHatch) hatch).getInputSource();
            if (source == null || !sources.add(source)) continue;
            for (BigPatternBuffer buffer : source.getBuffers()) {
                buffer.reconcile();
                List<IAEStack<?>> contents = buffer.getStacksBig();
                // Changed items are appended by the buffer: finish the partially charged item before starting another.
                for (int i = contents.size() - 1; i >= 0; i--) {
                    if (!(contents.get(i) instanceof IAEItemStack)) continue;
                    IAEItemStack type = (IAEItemStack) contents.get(i);
                    if (!buffer.canEditOneItem(type)) continue;
                    Slot slot = new Slot(source, buffer, type);
                    slots.add(slot);
                    buses.add(slot);
                    owned.add(slot.mInventory[0]);
                    break;
                }
            }
        }
    }

    public ArrayList<MTEHatchInputBus> buses() {
        return buses;
    }

    public boolean consume(ItemStack stack) {
        if (!owned.contains(stack)) return false;
        stack.stackSize = 0;
        return true;
    }

    @Override
    public void close() {
        for (Slot slot : slots) {
            ItemStack result = slot.mInventory[0];
            if (ItemStack.areItemStacksEqual(slot.before, result)) continue;
            slot.buffer.replaceOneItem(slot.type, result);
            slot.source.markDirty();
        }
        slots.clear();
        owned.clear();
    }

    private static final class Slot extends MTEHatchInputBus {

        final MTEInfinitePatternInputAssembly source;
        final BigPatternBuffer buffer;
        final IAEItemStack type;
        final ItemStack before;

        Slot(MTEInfinitePatternInputAssembly source, BigPatternBuffer buffer, IAEItemStack type) {
            super("apeiron.pattern_item_view", 0, 1, new String[0], null);
            this.source = source;
            this.buffer = buffer;
            this.type = type;
            mInventory[0] = type.getItemStack()
                .copy();
            mInventory[0].stackSize = 1;
            before = mInventory[0].copy();
        }
    }
}
