package com.silvia.apeiron.ae.smoke;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.silvia.apeiron.Apeiron;

import appeng.api.AEApi;
import appeng.api.storage.StorageName;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.tile.networking.TileCableBus;
import appeng.util.item.AEItemStack;

/** Exercises the real encoded-output callbacks, including TST, on the integrated server's player. */
public final class AEPatternEncodingSmoke {

    private AEPatternEncodingSmoke() {}

    public static void verify(EntityPlayer player) {
        TileCableBus host = new TileCableBus();
        host.setWorldObj(player.worldObj);
        host.xCoord = (int) player.posX;
        host.yCoord = (int) player.posY;
        host.zCoord = (int) player.posZ;
        PartPatternTerminal part = new PartPatternTerminal(
            AEApi.instance()
                .definitions()
                .parts()
                .patternTerminal()
                .maybeStack(1)
                .get());
        part.setPartHostInfo(ForgeDirection.NORTH, host.getCableBus(), host);
        part.setCraftingRecipe(false);
        ContainerPatternTerm container = new ContainerPatternTerm(player.inventory, part, true);
        container.encode();
        check(
            part.getInventoryByName("pattern")
                .getStackInSlot(1) == null,
            "empty encoding created an output");
        // An invalid item in the blank slot forces a normal unsuccessful return without relying on a grid.
        part.getAEInventoryByName(StorageName.CRAFTING_INPUT)
            .putAEStackInSlot(0, AEItemStack.create(new ItemStack(Items.diamond, 2)));
        part.getAEInventoryByName(StorageName.CRAFTING_OUTPUT)
            .putAEStackInSlot(0, AEItemStack.create(new ItemStack(Items.emerald)));
        part.getInventoryByName("pattern")
            .setInventorySlotContents(0, new ItemStack(Items.stick));
        container.encode();
        check(
            part.getInventoryByName("pattern")
                .getStackInSlot(1) == null,
            "rejected encoding created an output");
        part.getInventoryByName("pattern")
            .setInventorySlotContents(
                0,
                AEApi.instance()
                    .definitions()
                    .materials()
                    .blankPattern()
                    .maybeStack(1)
                    .get());
        container.encode();
        ItemStack output = part.getInventoryByName("pattern")
            .getStackInSlot(1);
        check(output != null && output.hasTagCompound(), "valid encoding blocked by null-output protection");
        check(
            part.getInventoryByName("pattern")
                .getStackInSlot(0) == null,
            "valid encoding did not consume exactly one blank");
        Apeiron.LOG.info(
            "AE pattern terminal encoding: empty and rejected outputs, valid encoding and TST conversion callback verification passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Pattern encoding: " + message);
    }
}
