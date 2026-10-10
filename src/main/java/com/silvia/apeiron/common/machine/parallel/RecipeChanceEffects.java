package com.silvia.apeiron.common.machine.parallel;

import cpw.mods.fml.common.Loader;
import gregtech.api.util.GTRecipe;

/** Optional recipe probability effects shared by exact GT, TST and custom item-particle execution. */
public final class RecipeChanceEffects {

    private RecipeChanceEffects() {}

    public static GTRecipe apply(Object machine, GTRecipe recipe, double multiplier) {
        return Loader.isModLoaded("sciencenotleisure")
            ? com.silvia.apeiron.common.integration.gtnl.parallel.GtnlRecipeChances.apply(machine, recipe, multiplier)
            : recipe;
    }
}
