// SPDX-License-Identifier: LGPL-3.0-only
package com.silvia.apeiron.common.block.crafting;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.common.tile.crafting.TileInfiniteCraftingUnit;

import appeng.block.crafting.BlockCraftingUnit;
import appeng.client.texture.ExtraBlockTextures;

public class BlockInfiniteCraftingUnit extends BlockCraftingUnit {

    public BlockInfiniteCraftingUnit() {
        setTileEntity(TileInfiniteCraftingUnit.class);
        setBlockName("apeiron.infinite_crafting_unit");
        setBlockTextureName("appliedenergistics2:BlockCraftingAccelerator4096x");
        setCreativeTab(ApeironMachines.CREATIVE_TAB);
    }

    @Override
    public String getTextureName() {
        return "appliedenergistics2:BlockCraftingAccelerator4096x";
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return "tile.apeiron.infinite_crafting_unit";
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        return (meta & 8) != 0 ? ExtraBlockTextures.BlockCraftingAccelerator4096xFit.getIcon()
            : ExtraBlockTextures.BlockCraftingAccelerator4096x.getIcon();
    }

    @Override
    public void getCheckedSubBlocks(Item item, CreativeTabs tab, List<ItemStack> stacks) {
        stacks.add(new ItemStack(this));
    }

    @Override
    public int damageDropped(int meta) {
        return 0;
    }
}
