// SPDX-License-Identifier: LGPL-3.0-only
package com.silvia.apeiron.common.block.crafting;

import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import com.silvia.apeiron.common.tile.crafting.TileInfiniteCraftingStorage;

import appeng.client.texture.ExtraBlockTextures;

public final class BlockInfiniteCraftingStorage extends BlockInfiniteCraftingUnit {

    public BlockInfiniteCraftingStorage() {
        setTileEntity(TileInfiniteCraftingStorage.class);
        setBlockName("apeiron.infinite_crafting_storage");
        setBlockTextureName("appliedenergistics2:BlockCraftingStorageSingularity");
    }

    @Override
    public String getTextureName() {
        return "appliedenergistics2:BlockCraftingStorageSingularity";
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return "tile.apeiron.infinite_crafting_storage";
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        return (meta & 8) != 0 ? ExtraBlockTextures.BlockCraftingStorageSingularityFit.getIcon() : this.blockIcon;
    }
}
