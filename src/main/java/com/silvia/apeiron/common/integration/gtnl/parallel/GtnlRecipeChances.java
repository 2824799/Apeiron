package com.silvia.apeiron.common.integration.gtnl.parallel;

import java.util.OptionalDouble;

import com.science.gtnl.config.MainConfig;
import com.science.gtnl.utils.enums.ModList;
import com.science.gtnl.utils.recipes.ChanceBonusManager;

import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

/** Reuses GTNL's provider ordering, configuration and blacklist without altering the registered recipe. */
public final class GtnlRecipeChances {

    private GtnlRecipeChances() {}

    public static GTRecipe apply(Object machine, GTRecipe recipe, double multiplier) {
        if (ModList.Overpowered.isModLoaded() || !MainConfig.machine.enableRecipeOutputChance) return recipe;
        OptionalDouble bonus = ChanceBonusManager
            .getChanceBonusOptional(machine, GTUtility.getTier(recipe.mEUt), multiplier, recipe);
        return bonus.isPresent() ? ChanceBonusManager.copyAndBonusChance(recipe, bonus.getAsDouble()) : recipe;
    }
}
