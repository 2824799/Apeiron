package com.silvia.apeiron.api.machine.parallel;

import net.minecraft.item.ItemStack;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;

/** A declarative single-item transformation source; the controller retains its running callbacks. */
public interface ItemProcessingRecipeSource {

    int getDurationTicks();

    ItemProcessingRecipe findRecipe(ItemStack input);

    /** Check research, mode and other startup conditions without consuming resources. */
    default CheckRecipeResult validate(MTEMultiBlockBase machine) {
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    default boolean returnsUnmatchedItems() {
        return false;
    }
}
