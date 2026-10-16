package com.silvia.apeiron.common.machine.block;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.common.blocks.BlockMachines;
import gregtech.common.blocks.MaterialMachines;

/** Hidden compatibility registration for machines placed before the migration to GregTech's block. */
public final class ApeironMachineBlock extends BlockMachines {

    public ApeironMachineBlock() {
        super(ApeironMachineItem.class, "apeiron.machines", new MaterialMachines());
        this.setCreativeTab(null);
    }

    @Override
    public String getUnlocalizedName() {
        return "machines";
    }

    @Override
    public TileEntity createTileEntity(final World world, final int metadata) {
        return new ApeironMachineTile();
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSubBlocks(final Item item, final CreativeTabs tab, final List stacks) {
        // GregTech's machine item provides the visible entries.
    }
}
