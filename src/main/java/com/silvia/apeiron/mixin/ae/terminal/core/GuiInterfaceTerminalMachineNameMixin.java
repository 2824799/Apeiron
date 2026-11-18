package com.silvia.apeiron.mixin.ae.terminal.core;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.client.gui.implementations.GuiInterfaceTerminal;
import gregtech.api.recipe.RecipeMap;

@Mixin(value = GuiInterfaceTerminal.class, remap = false)
public abstract class GuiInterfaceTerminalMachineNameMixin {

    @Inject(method = "translateRawName", at = @At("HEAD"), cancellable = true)
    private static void apeiron$localizeMachineTitle(String rawName, String suffix, ItemStack icon,
        CallbackInfoReturnable<String> cir) {
        if (rawName == null || !rawName.startsWith("gt.blockmachines.")
            || icon == null
            || !rawName.equals(icon.getUnlocalizedName())) return;

        // Some GT add-on machines use a different localization key from the item's raw registry name.
        String machineName = icon.getDisplayName();
        if (machineName.trim()
            .isEmpty() || machineName.startsWith("gt.blockmachines.")) {
            machineName = StatCollector.translateToFallback(rawName + ".name");
            if (machineName.trim()
                .isEmpty() || machineName.startsWith("gt.blockmachines."))
                machineName = StatCollector.translateToLocal("gt.blockmachines.name");
        }
        if (suffix == null || suffix.isEmpty()) {
            cir.setReturnValue(machineName);
            return;
        }

        IChatComponent component;
        try {
            component = IChatComponent.Serializer.func_150699_a(suffix);
        } catch (RuntimeException malformed) {
            return; // Let AE handle plain-text and malformed suffixes.
        }
        if (component != null) cir
            .setReturnValue(machineName + apeiron$removeDuplicateRecipe(component, machineName).getUnformattedText());
    }

    @Unique
    private static IChatComponent apeiron$removeDuplicateRecipe(IChatComponent component, String machineName) {
        List<IChatComponent> siblings = component.getSiblings();
        // GTNL attaches the recipe translation as the first child of the literal separator.
        if (component instanceof ChatComponentText separator && separator.getChatComponentText_TextValue()
            .equals(" - ")
            && !siblings.isEmpty()
            && siblings.get(0) instanceof ChatComponentTranslation recipe
            && RecipeMap.ALL_RECIPE_MAPS.containsKey(recipe.getKey())
            && EnumChatFormatting.getTextWithoutFormattingCodes(recipe.getUnformattedTextForChat())
                .trim()
                .equalsIgnoreCase(
                    EnumChatFormatting.getTextWithoutFormattingCodes(machineName)
                        .trim())) {
            IChatComponent remainder = new ChatComponentText("");
            for (IChatComponent child : recipe.getSiblings()) remainder.appendSibling(child);
            for (int i = 1; i < siblings.size(); i++) remainder.appendSibling(siblings.get(i));
            return remainder;
        }
        for (int i = 0; i < siblings.size(); i++) {
            siblings.set(i, apeiron$removeDuplicateRecipe(siblings.get(i), machineName));
        }
        return component;
    }
}
