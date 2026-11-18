package com.silvia.apeiron.common.integration.gtnl.parallel;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.OptionalDouble;
import java.util.function.BooleanSupplier;

import com.science.gtnl.config.MainConfig;
import com.science.gtnl.utils.enums.ModList;
import com.science.gtnl.utils.recipes.ChanceBonusManager;

import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

/** Reuses GTNL's provider ordering, configuration and blacklist without altering the registered recipe. */
public final class GtnlRecipeChances {

    private static final BooleanSupplier OUTPUT_CHANCES_ENABLED = findOutputChanceSwitch();

    private GtnlRecipeChances() {}

    public static GTRecipe apply(Object machine, GTRecipe recipe, double multiplier) {
        if (!OUTPUT_CHANCES_ENABLED.getAsBoolean()) return recipe;
        OptionalDouble bonus = ChanceBonusManager
            .getChanceBonusOptional(machine, GTUtility.getTier(recipe.mEUt), multiplier, recipe);
        return bonus.isPresent() ? ChanceBonusManager.copyAndBonusChance(recipe, bonus.getAsDouble()) : recipe;
    }

    private static BooleanSupplier findOutputChanceSwitch() {
        try {
            Class<?> outputChance = Class.forName(
                "com.science.gtnl.compat.overpowered.RecipeOutputChance",
                false,
                GtnlRecipeChances.class.getClassLoader());
            MethodHandle enabled = MethodHandles.publicLookup()
                .findStatic(outputChance, "enabled", MethodType.methodType(boolean.class));
            return () -> invokeEnabled(enabled);
        } catch (ClassNotFoundException | NoSuchMethodException oldGtnl) {
            return LegacyChanceSwitch::enabled;
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot access GTNL's recipe chance switch", error);
        }
    }

    private static boolean invokeEnabled(MethodHandle enabled) {
        try {
            return (boolean) enabled.invokeExact();
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot check GTNL's recipe chance switch", error);
        }
    }

    private static final class LegacyChanceSwitch {

        private static boolean enabled() {
            return !ModList.Overpowered.isModLoaded() && MainConfig.machine.enableRecipeOutputChance;
        }
    }
}
