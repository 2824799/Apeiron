package com.silvia.apeiron.ae.storage;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/** Access to AE's physical slot rules for exact simulations and transfers. */
public interface BigPhysicalInventoryAccess {

    IInventory apeiron$getInventory();

    boolean apeiron$skipsStackLimit();

    boolean apeiron$canRemove(int slot, ItemStack stack);
}
