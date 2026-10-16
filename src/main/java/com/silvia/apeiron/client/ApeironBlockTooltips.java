package com.silvia.apeiron.client;

import java.util.List;

import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.GameRegistry;

/** Adds Apeiron's ownership and stored-content information to both native GT and Apeiron block items. */
public final class ApeironBlockTooltips {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTooltip(ItemTooltipEvent event) {
        append(event.itemStack, event.toolTip);
    }

    public static void append(ItemStack stack, List<String> tooltip) {
        if (stack == null) return;
        boolean machine = ApeironMachines.isMachineStack(stack);
        if (!machine) {
            if (!(stack.getItem() instanceof ItemBlock)) return;
            GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(stack.getItem());
            if (id == null || !Apeiron.MODID.equals(id.modId)) return;
        }
        if (machine && stack.hasTagCompound()) {
            NBTTagCompound contents = stack.getTagCompound()
                .getCompoundTag("ApeironContents");
            for (String key : new String[] { "items", "fluids", "patterns", "installed" }) {
                if (contents.hasKey(key)) tooltip.add(
                    StatCollector.translateToLocalFormatted("apeiron.machine.stored." + key, contents.getString(key)));
            }
        }
        tooltip.add(StatCollector.translateToLocal("apeiron.tooltip.added_by"));
    }
}
