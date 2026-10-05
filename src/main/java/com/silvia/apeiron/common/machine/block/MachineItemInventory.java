package com.silvia.apeiron.common.machine.block;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import gregtech.api.metatileentity.CommonMetaTileEntity;
import gregtech.api.util.GTUtility;

/** Uses GT's native Inventory format so cells remain in the harvested machine, once. */
public final class MachineItemInventory {

    private MachineItemInventory() {}

    public static void write(CommonMetaTileEntity machine, NBTTagCompound tag) {
        NBTTagList inventory = new NBTTagList();
        for (int slot = 0; slot < machine.mInventory.length; slot++) {
            ItemStack stack = machine.mInventory[slot];
            if (stack == null || stack.stackSize <= 0) continue;
            NBTTagCompound entry = stack.writeToNBT(new NBTTagCompound());
            entry.setInteger("IntSlot", slot);
            if (stack.stackSize > Byte.MAX_VALUE) entry.setInteger("Count", stack.stackSize);
            inventory.appendTag(entry);
        }
        if (inventory.tagCount() > 0) tag.setTag("Inventory", inventory);
        else tag.removeTag("Inventory");
    }

    public static void read(CommonMetaTileEntity machine, NBTTagCompound tag) {
        if (!tag.hasKey("Inventory", 9)) return;
        java.util.Arrays.fill(machine.mInventory, null);
        NBTTagList inventory = tag.getTagList("Inventory", 10);
        for (int index = 0; index < inventory.tagCount(); index++) {
            NBTTagCompound entry = inventory.getCompoundTagAt(index);
            int slot = entry.getInteger("IntSlot");
            if (slot >= 0 && slot < machine.mInventory.length) machine.mInventory[slot] = GTUtility.loadItem(entry);
        }
    }
}
