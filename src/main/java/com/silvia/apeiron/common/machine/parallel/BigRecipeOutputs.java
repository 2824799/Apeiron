package com.silvia.apeiron.common.machine.parallel;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.math.RecipeOutputCounts;

import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.objects.XSTR;
import gregtech.api.util.GTRecipe;

/** Shared exact products; capacity checks use the upper bound and never draw random outputs. */
public final class BigRecipeOutputs {

    private BigRecipeOutputs() {}

    public static List<IAEStack<?>> calculate(MTEMultiBlockBase machine, GTRecipe recipe, BigInteger parallels,
        double chanceMultiplier, boolean capacity) {
        List<IAEStack<?>> values = new ArrayList<>();
        for (int i = 0; i < Math.min(recipe.mOutputs.length, machine.getItemOutputLimit()); i++) {
            int chance = (int) (recipe.getOutputChance(i) * chanceMultiplier);
            if (recipe.mOutputs[i] != null && chance > 0) values.add(
                BigAEStackValues.copyWithSize(
                    AEItemStack.create(recipe.mOutputs[i]),
                    count(parallels, chance, capacity).multiply(BigInteger.valueOf(recipe.mOutputs[i].stackSize))));
        }
        for (int i = 0; i < Math.min(recipe.mFluidOutputs.length, machine.getFluidOutputLimit()); i++) {
            int chance = (int) (recipe.getFluidOutputChance(i) * chanceMultiplier);
            if (recipe.mFluidOutputs[i] != null && chance > 0) values.add(
                BigAEStackValues.copyWithSize(
                    AEFluidStack.create(recipe.mFluidOutputs[i]),
                    count(parallels, chance, capacity).multiply(BigInteger.valueOf(recipe.mFluidOutputs[i].amount))));
        }
        return values;
    }

    private static BigInteger count(BigInteger parallels, int chance, boolean capacity) {
        return capacity ? RecipeOutputCounts.maximum(parallels, chance)
            : RecipeOutputCounts.roll(parallels, chance, XSTR.XSTR_INSTANCE);
    }
}
