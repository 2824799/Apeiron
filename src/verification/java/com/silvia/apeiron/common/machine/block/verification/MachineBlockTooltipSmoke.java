package com.silvia.apeiron.common.machine.block.verification;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import com.silvia.apeiron.common.block.crafting.ApeironCraftingBlocks;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;

import gregtech.api.GregTechAPI;

public final class MachineBlockTooltipSmoke {

    private MachineBlockTooltipSmoke() {}

    public static void verify() {
        String credit = StatCollector.translateToLocal("apeiron.tooltip.added_by");
        check(!credit.equals("apeiron.tooltip.added_by"), "credit is not translated");
        List<ItemStack> legacyEntries = new ArrayList<>();
        ApeironMachines.block.getSubBlocks(Item.getItemFromBlock(ApeironMachines.block), null, legacyEntries);
        check(legacyEntries.isEmpty(), "legacy block still publishes subitems");
        List<ItemStack> entries = new ArrayList<>();
        ApeironMachines.CREATIVE_TAB.displayAllReleventItems(entries);
        int machines = 0;
        for (ItemStack stack : entries) if (ApeironMachines.isMachineStack(stack)) {
            machines++;
            check(
                stack.getItem() == Item.getItemFromBlock(GregTechAPI.sBlockMachines),
                "creative tab still uses legacy machine items");
        }
        check(machines == 14, "creative tab omitted or duplicated machines");
        for (int id = ApeironConfig.getMachineIdStart(); id <= ApeironConfig.getMachineIdEnd(); id++) {
            if (!ApeironMachines.isApeironMachine(id)) continue;
            verifyCredit(GregTechAPI.METATILEENTITIES[id].getStackForm(1), credit);
            verifyCredit(new ItemStack(ApeironMachines.block, 1, id), credit);
        }
        verifyCredit(new ItemStack(ApeironCraftingBlocks.unit), credit);
        verifyCredit(new ItemStack(ApeironCraftingBlocks.storage), credit);
        check(
            !new ItemStack(Items.diamond).getTooltip(null, false)
                .contains(credit),
            "credited unrelated item");
        ItemStack stored = ApeironMachines.mixedOutputAssembly.getStackForm(1);
        NBTTagCompound contents = new NBTTagCompound();
        contents.setString("items", "12345678901234567890");
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("ApeironContents", contents);
        stored.setTagCompound(tag);
        List<String> lines = stored.getTooltip(null, false);
        check(
            lines.contains(
                StatCollector.translateToLocalFormatted("apeiron.machine.stored.items", "12345678901234567890")),
            "GT tooltip omitted stored contents");
        check(credit.equals(lines.get(lines.size() - 1)), "credit does not follow stored contents");
    }

    private static void verifyCredit(ItemStack stack, String credit) {
        List<String> lines = stack.getTooltip(null, false);
        check(credit.equals(lines.get(lines.size() - 1)), "credit is not the final tooltip line");
        check(lines.indexOf(credit) == lines.lastIndexOf(credit), "duplicate credit");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Machine block tooltip verification: " + message);
    }
}
