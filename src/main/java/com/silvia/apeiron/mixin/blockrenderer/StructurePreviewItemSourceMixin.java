package com.silvia.apeiron.mixin.blockrenderer;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gregtech.api.GregTechAPI;

/**
 * Keeps the structure projector's representative hatch stable when an optional Apeiron hatch also matches.
 *
 * <p>
 * StructureLib correctly accepts every matching hatch. BlockRenderer6343's creative item source chooses the first
 * matching stack, though, and the Apeiron machine item can be registered before GregTech's machine item. That makes a
 * preview of a mixed hatch position render every position as an Apeiron hatch. This client-only preference affects the
 * preview source only; the real structure predicate and machine registration remain unchanged.
 * </p>
 */
@Mixin(targets = "blockrenderer6343.api.utils.CreativeItemSource", remap = false)
@Pseudo
public abstract class StructurePreviewItemSourceMixin {

    private static volatile Field apeiron$itemListField;

    @Inject(method = "take", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$preferGregTechMachine(Predicate<ItemStack> predicate, boolean simulate, int count,
        CallbackInfoReturnable<Map<ItemStack, Integer>> cir) {
        if (predicate == null) return;
        Item originalMachineItem = Item.getItemFromBlock(GregTechAPI.sBlockMachines);
        if (originalMachineItem == null) return;

        for (ItemStack candidate : apeiron$getItemList()) {
            if (candidate == null || candidate.getItem() != originalMachineItem || !predicate.test(candidate)) continue;
            Map<ItemStack, Integer> result = new HashMap<>();
            result.put(candidate, Integer.MAX_VALUE);
            cir.setReturnValue(result);
            return;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> apeiron$getItemList() {
        try {
            Field field = apeiron$itemListField;
            if (field == null) {
                field = Class.forName("codechicken.nei.ItemList")
                    .getDeclaredField("items");
                if (!Modifier.isStatic(field.getModifiers())) return java.util.Collections.emptyList();
                field.setAccessible(true);
                apeiron$itemListField = field;
            }
            Object value = field.get(null);
            return value instanceof List ? (List<ItemStack>) value : java.util.Collections.emptyList();
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return java.util.Collections.emptyList();
        }
    }
}
