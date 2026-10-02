package com.silvia.apeiron.common.machine.me.input.storage;

import java.math.BigInteger;
import java.util.Collections;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;

/** Server-authoritative terminal-style transfers. The cursor never holds a saturated big stack. */
public final class PatternBufferTransfers {

    private PatternBufferTransfers() {}

    public static boolean depositInventory(MTEInfinitePatternInputAssembly machine, EntityPlayer player, int index,
        net.minecraft.inventory.Slot slot) {
        if (machine.isRecipeProcessing() || index < 0 || index >= MTEInfinitePatternInputAssembly.BUFFER_COUNT)
            return false;
        ItemStack input = slot.getStack();
        if (input == null || input.stackSize <= 0) return false;
        IAEFluidStack contained = appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE.getStackFromContainerItem(input);
        if (contained != null && BigAEStackValues.get(contained)
            .signum() > 0) {
            ItemStack cursor = player.inventory.getItemStack();
            ItemStack hand = input.copy();
            player.inventory.setItemStack(hand);
            try {
                if (!fluidClick(machine, player, index, null, hand)) return false;
                slot.putStack(player.inventory.getItemStack());
            } finally {
                player.inventory.setItemStack(cursor);
            }
        } else {
            IAEItemStack stack = AEItemStack.create(input);
            if (!machine.addToBufferBig(index, Collections.singletonList(stack))) return false;
            slot.putStack(null);
        }
        player.inventory.markDirty();
        return true;
    }

    public static IAEStack<?> stackAt(BigPatternBuffer buffer, int index, boolean fluid) {
        int current = 0;
        for (IAEStack<?> stack : buffer.getStacksBig()) if ((stack instanceof IAEFluidStack) == fluid) {
            if (current++ == index) return stack;
        }
        return null;
    }

    public static boolean click(MTEInfinitePatternInputAssembly machine, EntityPlayer player, int bufferIndex, int slot,
        boolean fluid, IAEStack<?> expected, int button, boolean shift) {
        if (machine.isRecipeProcessing() || bufferIndex < 0
            || bufferIndex >= 24
            || slot < 0
            || slot >= 32
            || button < 0
            || button > 1) return false;
        BigPatternBuffer buffer = machine.getBuffers()
            .get(bufferIndex);
        buffer.reconcile();
        IAEStack<?> stored = stackAt(buffer, slot, fluid);
        // A stale page/slot packet must not extract a different item after the list was compacted.
        if ((stored == null) != (expected == null) || stored != null && !sameType(stored, expected)) return false;
        ItemStack hand = player.inventory.getItemStack();
        boolean changed = fluid ? fluidClick(machine, player, bufferIndex, stored, hand)
            : itemClick(machine, player, bufferIndex, stored, hand, button, shift);
        if (changed) {
            player.inventory.markDirty();
            if (player.openContainer != null) player.openContainer.detectAndSendChanges();
            if (player instanceof EntityPlayerMP) ((EntityPlayerMP) player).updateHeldItem();
        }
        return changed;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static boolean sameType(IAEStack<?> a, IAEStack<?> b) {
        return a.getClass() == b.getClass() && ((IAEStack) a).isSameType(b);
    }

    private static boolean itemClick(MTEInfinitePatternInputAssembly machine, EntityPlayer player, int index,
        IAEStack<?> stored, ItemStack hand, int button, boolean shift) {
        if (hand != null) {
            if (hand.stackSize <= 0) return false;
            IAEItemStack type = AEItemStack.create(hand);
            if (stored != null && !sameType(stored, type)) return false;
            int count = button == 1 ? 1 : hand.stackSize;
            BigAEStackValues.set(type, BigInteger.valueOf(count));
            if (!machine.addToBufferBig(index, Collections.singletonList(type))) return false;
            hand.stackSize -= count;
            if (hand.stackSize == 0) player.inventory.setItemStack(null);
            return true;
        }
        if (!(stored instanceof IAEItemStack)) return false;
        BigInteger available = BigAEStackValues.get(stored);
        ItemStack physical = ((IAEItemStack) stored).getItemStack();
        int max = physical.getMaxStackSize();
        if (shift) {
            int accepted = addToInventory(
                player,
                physical,
                available.min(BigInteger.valueOf(36L * max))
                    .intValue(),
                true);
            if (accepted <= 0) return false;
            machine.removeFromBufferBig(index, stored, BigInteger.valueOf(accepted));
            addToInventory(player, physical, accepted, false);
        } else {
            int count = available.min(BigInteger.valueOf(max))
                .intValue();
            if (button == 1) count = (count + 1) / 2;
            if (count <= 0) return false;
            machine.removeFromBufferBig(index, stored, BigInteger.valueOf(count));
            physical.stackSize = count;
            player.inventory.setItemStack(physical);
        }
        return true;
    }

    private static int addToInventory(EntityPlayer player, ItemStack type, int count, boolean simulate) {
        int remaining = count;
        for (int pass = 0; pass < 2; pass++) for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack slot = player.inventory.mainInventory[i];
            if ((pass == 0) != (slot != null)) continue;
            if (slot != null && (!slot.isItemEqual(type) || !ItemStack.areItemStackTagsEqual(slot, type))) continue;
            int limit = Math.min(type.getMaxStackSize(), player.inventory.getInventoryStackLimit());
            int added = Math.min(remaining, Math.max(0, limit - (slot == null ? 0 : slot.stackSize)));
            if (added == 0) continue;
            if (!simulate) {
                if (slot == null) {
                    slot = type.copy();
                    slot.stackSize = 0;
                    player.inventory.mainInventory[i] = slot;
                }
                slot.stackSize += added;
            }
            remaining -= added;
            if (remaining == 0) return count;
        }
        return count - remaining;
    }

    private static boolean fluidClick(MTEInfinitePatternInputAssembly machine, EntityPlayer player, int index,
        IAEStack<?> stored, ItemStack hand) {
        if (hand == null || hand.stackSize <= 0) return false;
        ItemStack single = hand.copy();
        single.stackSize = 1;
        appeng.util.item.AEFluidStackType type = appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;
        if (!type.isContainerItemForType(single)) return false;
        IAEFluidStack contained = type.getStackFromContainerItem(single);
        boolean deposit = contained != null && BigAEStackValues.get(contained)
            .signum() > 0;
        it.unimi.dsi.fastutil.objects.ObjectLongPair<ItemStack> converted;
        IAEFluidStack descriptor;
        if (deposit) {
            descriptor = contained;
            converted = type.drainStackFromContainer(single, contained.copy());
        } else {
            if (!(stored instanceof IAEFluidStack)) return false;
            descriptor = (IAEFluidStack) stored;
            converted = type.fillContainer(single, descriptor.copy());
        }
        if (converted == null || converted.left() == null || converted.rightLong() <= 0) return false;
        ItemStack result = converted.left();
        BigInteger amount = BigInteger.valueOf(converted.rightLong());
        IAEFluidStack transfer = BigAEStackValues.copyWithSize(descriptor, amount);
        if (stored != null && !sameType(stored, transfer)) return false;
        if (hand.stackSize > 1 && addToInventory(player, result, 1, true) != 1) return false;
        if (deposit) {
            if (!machine.addToBufferBig(index, Collections.singletonList(transfer))) return false;
        } else {
            if (stored == null || BigAEStackValues.get(stored)
                .compareTo(amount) < 0) return false;
            machine.removeFromBufferBig(index, stored, amount);
        }
        if (hand.stackSize == 1) player.inventory.setItemStack(result);
        else {
            hand.stackSize--;
            addToInventory(player, result, 1, false);
        }
        return true;
    }
}
