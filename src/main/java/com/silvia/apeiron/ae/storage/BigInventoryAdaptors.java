package com.silvia.apeiron.ae.storage;

import java.math.BigInteger;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.silvia.apeiron.ae.stack.BigAEItemStacks;
import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.api.config.Actionable;
import appeng.api.config.InsertionMode;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.AdaptorDualityInterface;
import appeng.util.inv.AdaptorIInventory;

/** Transfers exact AE counts across physical inventories without repeating simulated capacity. */
public final class BigInventoryAdaptors {

    public static final BigInteger MAX_EXTERNAL_CHUNK = BigInteger.valueOf(Integer.MAX_VALUE - 1L);

    private BigInventoryAdaptors() {}

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static IAEStack<?> addStackBig(final InventoryAdaptor adaptor, final IAEStack<?> input,
        final InsertionMode insertionMode, final boolean simulate) {
        if (input == null) return null;
        if (adaptor == null) return input;
        final BigInteger requested = BigAEStackValues.get(input);
        if (requested.signum() <= 0) return input;
        if (adaptor instanceof AdaptorDualityInterface) {
            final appeng.helpers.DualityInterface dual = ((AdaptorDualityInterface) adaptor).interfaceHost
                .getInterfaceDuality();
            final IMEMonitor monitor = dual.getMEMonitor(input.getStackType());
            return monitor == null ? input
                : BigMEInventories.injectItemsBig(
                    monitor,
                    input.copy(),
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE,
                    dual.getActionSource());
        }
        if (adaptor.getClass() == AdaptorIInventory.class && input instanceof IAEItemStack) {
            return insertPhysical((BigPhysicalInventoryAccess) adaptor, (IAEItemStack) input, insertionMode, simulate);
        }
        // A monitor-backed adaptor already accepts AE stacks without conversion to ItemStack.
        if (adaptor instanceof appeng.util.inv.AdaptorMEChest) {
            return simulate ? adaptor.simulateAddStack(input.copy(), insertionMode)
                : adaptor.addStack(input.copy(), insertionMode);
        }
        BigInteger remaining = requested;
        do {
            final BigInteger chunk = remaining.min(MAX_EXTERNAL_CHUNK);
            final IAEStack<?> offered = BigAEStackValues.copyWithSize(input, chunk);
            final IAEStack<?> leftover = simulate ? adaptor.simulateAddStack(offered, insertionMode)
                : adaptor.addStack(offered, insertionMode);
            final BigInteger rejected = BigAEStackValues.get(leftover);
            checkReturnedAmount(rejected, chunk);
            final BigInteger accepted = chunk.subtract(rejected);
            remaining = remaining.subtract(accepted);
            // Simulation cannot reserve capacity through this int API; never simulate it twice.
            if (simulate || accepted.signum() == 0 || rejected.signum() > 0) break;
        } while (remaining.signum() > 0);
        return remaining.signum() == 0 ? null : BigAEStackValues.copyWithSize(input, remaining);
    }

    private static IAEItemStack insertPhysical(final BigPhysicalInventoryAccess access, final IAEItemStack input,
        final InsertionMode mode, final boolean simulate) {
        final IInventory inventory = access.apeiron$getInventory();
        final int slots = inventory.getSizeInventory();
        final ItemStack template = input.getItemStack();
        final int limit = access.apeiron$skipsStackLimit() ? inventory.getInventoryStackLimit()
            : Math.min(inventory.getInventoryStackLimit(), template.getMaxStackSize());
        if (limit <= 0) return input;
        BigInteger remaining = BigAEStackValues.get(input);
        final boolean[] reserved = mode == InsertionMode.DEFAULT ? null : new boolean[slots];
        final int passes = mode == InsertionMode.DEFAULT || mode == InsertionMode.ONLY_EMPTY ? 1 : 2;
        boolean changed = false;
        for (int pass = 0; pass < passes && remaining.signum() > 0; pass++) {
            final boolean emptyOnly = mode != InsertionMode.DEFAULT && pass == 0;
            for (int slot = 0; slot < slots && remaining.signum() > 0; slot++) {
                if (reserved != null && reserved[slot]) continue;
                final ItemStack stored = inventory.getStackInSlot(slot);
                if (stored != null && (emptyOnly || !Platform.isSameItemPrecise(stored, template))) continue;
                final int room = stored == null ? limit : Math.max(0, limit - stored.stackSize);
                if (room == 0) continue;
                final int offered = remaining.min(BigInteger.valueOf(limit))
                    .intValue();
                final ItemStack probe = template.copy();
                probe.stackSize = offered;
                if (!inventory.isItemValidForSlot(slot, probe)) continue;
                final int accepted = Math.min(offered, room);
                remaining = remaining.subtract(BigInteger.valueOf(accepted));
                if (reserved != null && stored == null) reserved[slot] = true;
                if (!simulate) {
                    final ItemStack updated = stored == null ? template.copy() : stored.copy();
                    updated.stackSize = (stored == null ? 0 : stored.stackSize) + accepted;
                    inventory.setInventorySlotContents(slot, updated);
                    inventory.markDirty();
                    changed = true;
                }
            }
        }
        return remaining.signum() == 0 ? null : BigAEItemStacks.copyWithSize(input, remaining);
    }

    public static IAEItemStack extractStackBig(final InventoryAdaptor adaptor, final IAEItemStack request,
        final boolean simulate) {
        if (request == null || adaptor == null) return null;
        BigInteger remaining = BigAEStackValues.get(request);
        if (remaining.signum() <= 0) return null;
        if (adaptor.getClass() == AdaptorIInventory.class) {
            final BigPhysicalInventoryAccess access = (BigPhysicalInventoryAccess) adaptor;
            final IInventory inventory = access.apeiron$getInventory();
            final ItemStack template = request.getItemStack();
            BigInteger extracted = BigInteger.ZERO;
            for (int slot = 0; slot < inventory.getSizeInventory() && remaining.signum() > 0; slot++) {
                final ItemStack stored = inventory.getStackInSlot(slot);
                if (stored == null || stored.stackSize <= 0
                    || !access.apeiron$canRemove(slot, stored)
                    || !Platform.isSameItemPrecise(stored, template)) continue;
                final int amount = remaining.min(BigInteger.valueOf(stored.stackSize))
                    .intValue();
                final int actual;
                if (simulate) actual = amount;
                else {
                    final ItemStack removed = inventory.decrStackSize(slot, amount);
                    actual = removed == null ? 0 : removed.stackSize;
                    if (actual < 0 || actual > amount
                        || removed != null && !Platform.isSameItemPrecise(removed, template)) {
                        throw new IllegalStateException("Physical inventory returned an invalid extraction");
                    }
                    inventory.markDirty();
                }
                remaining = remaining.subtract(BigInteger.valueOf(actual));
                extracted = extracted.add(BigInteger.valueOf(actual));
            }
            return extracted.signum() == 0 ? null : BigAEItemStacks.copyWithSize(request, extracted);
        }
        BigInteger extracted = BigInteger.ZERO;
        do {
            final int amount = remaining.min(MAX_EXTERNAL_CHUNK)
                .intValue();
            final ItemStack template = request.getItemStack();
            template.stackSize = amount;
            final ItemStack removed = simulate ? adaptor.simulateRemove(amount, template, null)
                : adaptor.removeItems(amount, template, null);
            if (removed == null || removed.stackSize <= 0) break;
            checkReturnedAmount(BigInteger.valueOf(removed.stackSize), BigInteger.valueOf(amount));
            extracted = extracted.add(BigInteger.valueOf(removed.stackSize));
            remaining = remaining.subtract(BigInteger.valueOf(removed.stackSize));
            if (simulate || removed.stackSize < amount) break;
        } while (remaining.signum() > 0);
        return extracted.signum() == 0 ? null : BigAEItemStacks.copyWithSize(request, extracted);
    }

    public static void checkReturnedAmount(final BigInteger returned, final BigInteger offered) {
        if (returned.signum() < 0 || returned.compareTo(offered) > 0) {
            throw new IllegalStateException("Inventory returned an invalid transfer amount");
        }
    }
}
