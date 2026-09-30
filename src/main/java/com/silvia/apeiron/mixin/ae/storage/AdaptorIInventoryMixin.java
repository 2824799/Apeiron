package com.silvia.apeiron.mixin.ae.storage;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.silvia.apeiron.ae.storage.BigPhysicalInventoryAccess;

import appeng.util.inv.AdaptorIInventory;

@Mixin(value = AdaptorIInventory.class, remap = false)
public interface AdaptorIInventoryMixin extends BigPhysicalInventoryAccess {

    @Override
    @Accessor("i")
    IInventory apeiron$getInventory();

    @Override
    @Accessor("skipStackSizeCheck")
    boolean apeiron$skipsStackLimit();

    @Override
    @Invoker("canRemoveStackFromSlot")
    boolean apeiron$canRemove(int slot, ItemStack stack);
}
