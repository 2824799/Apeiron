package com.silvia.apeiron.common.machine.block;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.silvia.apeiron.common.machine.registration.ApeironMachines;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.common.blocks.BlockMachines;
import gregtech.common.blocks.MaterialMachines;

/** Apeiron-owned block/item registration using GregTech's machine placement and rendering implementation. */
public final class ApeironMachineBlock extends BlockMachines {

    public ApeironMachineBlock() {
        super(ApeironMachineItem.class, "apeiron.machines", new MaterialMachines());
        this.setCreativeTab(ApeironMachines.CREATIVE_TAB);
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
        if (ApeironMachines.itemOutputBus != null) stacks.add(ApeironMachines.itemOutputBus.getStackForm(1L));
        if (ApeironMachines.fluidOutputHatch != null) stacks.add(ApeironMachines.fluidOutputHatch.getStackForm(1L));
        if (ApeironMachines.mixedOutputAssembly != null)
            stacks.add(ApeironMachines.mixedOutputAssembly.getStackForm(1L));
        if (ApeironMachines.infiniteEnergyHatch != null)
            stacks.add(ApeironMachines.infiniteEnergyHatch.getStackForm(1L));
        if (ApeironMachines.ultimateEnergyHatch != null)
            stacks.add(ApeironMachines.ultimateEnergyHatch.getStackForm(1L));
        if (ApeironMachines.patternInputAssembly != null)
            stacks.add(ApeironMachines.patternInputAssembly.getStackForm(1L));
        if (ApeironMachines.patternInputMirror != null) stacks.add(ApeironMachines.patternInputMirror.getStackForm(1L));
        if (ApeironMachines.circuitProvider != null) stacks.add(ApeironMachines.circuitProvider.getStackForm(1L));
        if (ApeironMachines.storageInputBus != null) stacks.add(ApeironMachines.storageInputBus.getStackForm(1L));
        if (ApeironMachines.storageInputHatch != null) stacks.add(ApeironMachines.storageInputHatch.getStackForm(1L));
        if (ApeironMachines.storageInputAssembly != null)
            stacks.add(ApeironMachines.storageInputAssembly.getStackForm(1L));
        if (ApeironMachines.eyeOfHarmonyEnhancementModule != null)
            stacks.add(ApeironMachines.eyeOfHarmonyEnhancementModule.getStackForm(1L));
    }
}
