package com.silvia.apeiron.common.tile.crafting;

import net.minecraft.item.ItemStack;

import com.silvia.apeiron.common.block.crafting.ApeironCraftingBlocks;

import appeng.tile.crafting.TileCraftingTile;

public class TileInfiniteCraftingUnit extends TileCraftingTile {

    @Override
    public boolean isAccelerator() {
        return true;
    }

    @Override
    public int acceleratorValue() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected ItemStack getItemFromTile(Object tile) {
        return new ItemStack(ApeironCraftingBlocks.unit);
    }
}
