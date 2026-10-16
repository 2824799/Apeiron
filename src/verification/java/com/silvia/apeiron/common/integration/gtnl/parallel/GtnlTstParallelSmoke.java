package com.silvia.apeiron.common.integration.gtnl.parallel;

import java.lang.reflect.Field;
import java.util.LinkedList;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.Nxer.TwistSpaceTechnology.common.machine.GTCM_CrystallineInfinitier;
import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.processingLogics.GTCM_ParallelHelper;
import com.science.gtnl.config.MainConfig;
import com.science.gtnl.utils.enums.ModList;
import com.science.gtnl.utils.recipes.ChanceBonusManager;
import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.compat.DependencyCapabilities;

import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;

/** Exercise TST's native builder with the real GTNL chance hook, independently of Apeiron energy hatches. */
public final class GtnlTstParallelSmoke {

    private GtnlTstParallelSmoke() {}

    public static void verify() throws ReflectiveOperationException {
        if (DependencyCapabilities.hasDeclaredField(GTCM_ParallelHelper.class.getName(), "recipe", null)
            || ModList.Overpowered.isModLoaded()) return;
        final boolean enabled = MainConfig.machine.enableRecipeOutputChance;
        final var providers = new LinkedList<>(ChanceBonusManager.bonusProviders);
        try {
            for (boolean bonus : new boolean[] { true, false }) {
                MainConfig.machine.enableRecipeOutputChance = bonus;
                final var machine = new GTCM_CrystallineInfinitier("apeiron.verify.tst.chance");
                final int[] calls = { 0 };
                ChanceBonusManager.bonusProviders.clear();
                ChanceBonusManager.addFirstBonusProvider((candidate, tier, multiplier, recipe) -> {
                    if (candidate != machine) throw new IllegalStateException("TST chance hook lost its controller");
                    calls[0]++;
                    return 0.75;
                });
                final GTRecipe recipe = GTRecipeBuilder.builder()
                    .itemInputs(new ItemStack(Items.diamond))
                    .itemOutputs(new ItemStack(Items.emerald))
                    .outputChances(2500)
                    .eut(32)
                    .duration(20)
                    .build()
                    .get();
                final var helper = new GTCM_ParallelHelper();
                helper.enableLongOutputCalculation();
                helper.setRecipe(recipe)
                    .setMachine(machine, false, false)
                    .setAvailableEUt(32)
                    .setMaxParallel(1)
                    .setConsumption(false)
                    .setItemInputs(new ItemStack(Items.diamond))
                    .setOutputCalculation(true)
                    .build();
                if (calls[0] != (bonus ? 1 : 0)) throw new IllegalStateException(
                    "TST probability hook ran an incorrect number of times: " + calls[0]);
                final GTRecipe adjusted = (GTRecipe) field(helper, "recipe").get(helper);
                if (adjusted.getOutputChance(0) != (bonus ? 10000 : 2500) || recipe.getOutputChance(0) != 2500)
                    throw new IllegalStateException("TST chance bonus was lost or mutated the registered recipe");
                if (helper.getCurrentParallel() != 1)
                    throw new IllegalStateException("TST chance compatibility changed native parallel calculation");
                if (bonus && (helper.getLongItemOutputs()
                    .size() != 1
                    || helper.getLongItemOutputs()
                        .get(0)
                        .stackSize() != 1))
                    throw new IllegalStateException("TST native boosted output was not produced exactly once");
            }
        } finally {
            MainConfig.machine.enableRecipeOutputChance = enabled;
            ChanceBonusManager.bonusProviders.clear();
            ChanceBonusManager.bonusProviders.addAll(providers);
        }
        Apeiron.LOG.info(
            "TST inherited parallel state verification passed: native GTNL bonus once, disabled bonus, recipe isolation and native output");
    }

    private static Field field(Object instance, String name) throws ReflectiveOperationException {
        for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
}
