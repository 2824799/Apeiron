package com.silvia.apeiron.common.tile.crafting;

import net.minecraft.item.ItemStack;

import com.silvia.apeiron.common.block.crafting.ApeironCraftingBlocks;

import appeng.tile.crafting.TileCraftingStorageTile;

/** AE discovers CPU clusters through the storage-tile class hierarchy. */
public final class TileInfiniteCraftingStorage extends TileCraftingStorageTile {

    @Override
    public boolean isAccelerator() {
        return false;
    }

    @Override
    public boolean isStorage() {
        return true;
    }

    /** Old structural APIs get a positive projection. The CPU uses a separate unlimited flag. */
    @Override
    public long getStorageBytes() {
        return 1;
    }

    @Override
    protected ItemStack getItemFromTile(Object tile) {
        return new ItemStack(ApeironCraftingBlocks.storage);
    }
}
