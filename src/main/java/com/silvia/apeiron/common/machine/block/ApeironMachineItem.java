package com.silvia.apeiron.common.machine.block;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.silvia.apeiron.common.machine.registration.ApeironMachines;

import gregtech.api.GregTechAPI;
import gregtech.common.blocks.ItemMachines;

public final class ApeironMachineItem extends ItemMachines {

    public ApeironMachineItem(Block block) {
        super(block);
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ, int metadata) {
        if (!ApeironMachines.isMachineStack(stack)) return false;
        MachineItemNbt.migrate(stack);
        ItemMachines nativeItem = (ItemMachines) Item.getItemFromBlock(GregTechAPI.sBlockMachines);
        return nativeItem.placeBlockAt(stack, player, world, x, y, z, side, hitX, hitY, hitZ, metadata);
    }
}
