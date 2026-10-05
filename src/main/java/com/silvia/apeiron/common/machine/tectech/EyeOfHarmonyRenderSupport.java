package com.silvia.apeiron.common.machine.tectech;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import tectech.recipe.EyeOfHarmonyRecipe;
import tectech.thing.block.TileEntityEyeOfHarmony;
import tectech.thing.casing.TTCasingsContainer;
import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;

/** Reuses the native renderer during consecutive enhanced recipes instead of replacing it every tick. */
public final class EyeOfHarmonyRenderSupport {

    private EyeOfHarmonyRenderSupport() {}

    public static boolean canReuse(MTEEyeOfHarmony machine, EyeOfHarmonyRecipe recipe) {
        IGregTechTileEntity tile = machine.getBaseMetaTileEntity();
        World world = tile.getWorld();
        ForgeDirection back = machine.getExtendedFacing()
            .getRelativeBackInWorld();
        int x = tile.getXCoord() + 16 * back.offsetX;
        int y = tile.getYCoord() + 16 * back.offsetY;
        int z = tile.getZCoord() + 16 * back.offsetZ;
        if (world.getBlock(x, y, z) != TTCasingsContainer.eyeOfHarmonyRenderBlock) return false;
        TileEntity existing = world.getTileEntity(x, y, z);
        if (!(existing instanceof TileEntityEyeOfHarmony)) return false;
        TileEntityEyeOfHarmony renderer = (TileEntityEyeOfHarmony) existing;
        double size = 0.4D + (int) recipe.getSpacetimeCasingTierRequired() / 8.0D;
        return renderer.getTier() == recipe.getRocketTier() && renderer.getStarSize() == size;
    }
}
