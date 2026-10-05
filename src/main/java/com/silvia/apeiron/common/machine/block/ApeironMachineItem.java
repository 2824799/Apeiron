package com.silvia.apeiron.common.machine.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import gregtech.common.blocks.ItemMachines;

public final class ApeironMachineItem extends ItemMachines {

    public ApeironMachineItem(Block block) {
        super(block);
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean held) {
        if (!world.isRemote) MachineItemNbt.normalize(stack);
        super.onUpdate(stack, world, entity, slot, held);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        if (!stack.hasTagCompound()) return;
        NBTTagCompound contents = stack.getTagCompound()
            .getCompoundTag("ApeironContents");
        for (String key : new String[] { "items", "fluids", "patterns", "installed" }) {
            if (contents.hasKey(key)) tooltip
                .add(StatCollector.translateToLocalFormatted("apeiron.machine.stored." + key, contents.getString(key)));
        }
    }
}
