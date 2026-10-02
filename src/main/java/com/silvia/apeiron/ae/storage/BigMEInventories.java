package com.silvia.apeiron.ae.storage;

import java.util.Objects;
import java.util.Optional;

import com.silvia.apeiron.ae.stack.BigAEItemStacks;
import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.ItemList;

/** Dispatch exact-count operations without silently passing a saturated long to a legacy backend. */
public final class BigMEInventories {

    private BigMEInventories() {}

    public static boolean hasUnlimitedCapacity(final IMEInventory<?> inventory) {
        Object current = inventory;
        for (int depth = 0; depth < 8 && current != null; depth++) {
            if (current instanceof BigUnlimitedMEInventory) return true;
            if (!(current instanceof IMEInventoryHandler)) return false;
            final Object next = ((IMEInventoryHandler<?>) current).getInternal();
            if (next == current) return false;
            current = next;
        }
        return false;
    }

    public static boolean isUnlimitedCell(final net.minecraft.item.ItemStack stack,
        final appeng.api.storage.data.IAEStackType<?> type) {
        return stack != null && hasUnlimitedCapacity(
            appeng.api.AEApi.instance()
                .registries()
                .cell()
                .getCellInventory(stack, null, type));
    }

    /** Dispatch an exact operation for any AE stack type without passing a saturated long to legacy code. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <StackType extends IAEStack> StackType injectItemsBig(IMEInventory<StackType> inventory,
        StackType input, Actionable mode, BaseActionSource source) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(mode, "mode");
        if (input == null || input.getStackSize() == 0) return null;
        if (input.getStackSize() < 0) throw new IllegalArgumentException("negative injection count");
        if (inventory instanceof BigIMEInventory && input instanceof IAEItemStack) {
            return (StackType) ((BigIMEInventory) inventory).injectItemsBig((IAEItemStack) input, mode, source);
        }
        if (inventory instanceof BigMEInventory) {
            return (StackType) ((BigMEInventory) inventory).injectItemsBig(input, mode, source);
        }
        if (BigAEStackValues.isBig((IAEStack<?>) input)) {
            throw new UnsupportedBigInventoryException(inventory);
        }
        return inventory.injectItems(input, mode, source);
    }

    /** Dispatch an exact extraction for any AE stack type without passing a saturated long to legacy code. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <StackType extends IAEStack> StackType extractItemsBig(IMEInventory<StackType> inventory,
        StackType request, Actionable mode, BaseActionSource source) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(mode, "mode");
        if (request == null || request.getStackSize() == 0) return null;
        if (request.getStackSize() < 0) throw new IllegalArgumentException("negative extraction count");
        if (inventory instanceof BigIMEInventory && request instanceof IAEItemStack) {
            return (StackType) ((BigIMEInventory) inventory).extractItemsBig((IAEItemStack) request, mode, source);
        }
        if (inventory instanceof BigMEInventory) {
            return (StackType) ((BigMEInventory) inventory).extractItemsBig(request, mode, source);
        }
        if (BigAEStackValues.isBig((IAEStack<?>) request)) {
            throw new UnsupportedBigInventoryException(inventory);
        }
        return inventory.extractItems(request, mode, source);
    }

    public static IAEItemStack injectItemsBig(IMEInventory<IAEItemStack> inventory, IAEItemStack input, Actionable mode,
        BaseActionSource source) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(mode, "mode");
        if (input == null || input.getStackSize() == 0) return null;
        if (input.getStackSize() < 0) throw new IllegalArgumentException("negative injection count");
        if (inventory instanceof BigIMEInventory) {
            return ((BigIMEInventory) inventory).injectItemsBig(input, mode, source);
        }
        if (inventory instanceof BigMEInventory) {
            return (IAEItemStack) ((BigMEInventory) inventory).injectItemsBig(input, mode, source);
        }
        if (BigAEItemStacks.isStackSizeBig(input)) {
            throw new UnsupportedBigInventoryException(inventory);
        }
        return inventory.injectItems(input, mode, source);
    }

    public static IAEItemStack extractItemsBig(IMEInventory<IAEItemStack> inventory, IAEItemStack request,
        Actionable mode, BaseActionSource source) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(mode, "mode");
        if (request == null || request.getStackSize() == 0) return null;
        if (request.getStackSize() < 0) throw new IllegalArgumentException("negative extraction count");
        if (inventory instanceof BigIMEInventory) {
            return ((BigIMEInventory) inventory).extractItemsBig(request, mode, source);
        }
        if (inventory instanceof BigMEInventory) {
            return (IAEItemStack) ((BigMEInventory) inventory).extractItemsBig(request, mode, source);
        }
        if (BigAEItemStacks.isStackSizeBig(request)) {
            throw new UnsupportedBigInventoryException(inventory);
        }
        return inventory.extractItems(request, mode, source);
    }

    public static IAEItemStack getAvailableItemBig(IMEInventory<IAEItemStack> inventory, IAEItemStack request,
        int iteration) {
        Objects.requireNonNull(request, "request");
        IAEItemStack stack = inventory
            .getAvailableItems(new ItemList(), iteration, Optional.of(item -> item.isSameType(request)))
            .findPrecise(request);
        return stack == null ? null : stack.copy();
    }

    /** Exact available-item lookup for generic AE stacks. */
    public static <StackType extends IAEStack> StackType getAvailableItemBig(IMEInventory<StackType> inventory,
        StackType request, int iteration) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(request, "request");
        StackType stack = (StackType) inventory.getAvailableItems(
            request.getStackType()
                .createList(),
            iteration,
            Optional.of(item -> item.isSameType(request)))
            .findPrecise(request);
        return stack == null ? null : (StackType) stack.copy();
    }
}
