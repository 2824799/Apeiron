// SPDX-License-Identifier: LGPL-3.0-only
// Fluid-mode item conversion adapted from GT5-Unofficial 5.09.54.190, MTEQuantumForceTransformer.
package com.silvia.apeiron.common.machine.quantum;

import java.math.BigInteger;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.common.machine.me.input.storage.BigPatternBuffer;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.parallel.BigRecipeInventory;

import appeng.api.storage.data.IAEStack;
import gregtech.api.enums.Materials;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.objects.ItemData;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeConstants;
import gregtech.api.util.GTUtility;

/** Keep catalyst selection inside one isolated pattern and calculate guaranteed outputs without int projections. */
public final class QuantumEnhancementRecipes {

    private QuantumEnhancementRecipes() {}

    public static boolean hasCatalyst(MTEMultiBlockBase machine, GTRecipe recipe, ItemStack[] items,
        FluidStack[] fluids) {
        ItemStack required = recipe.getMetadata(GTRecipeConstants.QFT_CATALYST);
        if (required == null) return false;
        BigPatternBuffer buffer = BigRecipeInventory.findPatternBuffer(machine, items, fluids);
        if (buffer == null) return false;
        for (ItemStack selector : buffer.getSelectors())
            if (GTUtility.areStacksEqual(required, selector, false)) return true;
        return false;
    }

    public static FluidStack[] fluidModeItems(GTRecipe recipe, boolean fluidMode) {
        FluidStack[] converted = new FluidStack[recipe.mOutputs.length];
        if (!fluidMode) return converted;
        for (int i = 0; i < recipe.mOutputs.length; i++) {
            ItemStack item = recipe.mOutputs[i];
            if (item == null) continue;
            ItemData data = GTOreDictUnificator.getAssociation(item);
            Materials material = data == null ? null : data.mMaterial.mMaterial;
            if (material == null) continue;
            if (material.mStandardMoltenFluid != null) converted[i] = material.getMolten(144);
            else if (material.mFluid != null) converted[i] = material.getFluid(1000);
        }
        return converted;
    }

    public static List<IAEStack<?>> outputs(GTRecipe recipe, FluidStack[] converted, BigInteger parallels) {
        BigMachineOutputQueue outputs = new BigMachineOutputQueue();
        for (int i = 0; i < recipe.mOutputs.length; i++) {
            ItemStack item = recipe.mOutputs[i];
            if (item == null) continue;
            BigInteger amount = parallels.multiply(BigInteger.valueOf(item.stackSize));
            if (converted[i] == null) outputs.addItem(item, amount);
            else outputs.addFluid(converted[i], amount.multiply(BigInteger.valueOf(converted[i].amount)));
        }
        for (FluidStack fluid : recipe.mFluidOutputs)
            if (fluid != null) outputs.addFluid(fluid, parallels.multiply(BigInteger.valueOf(fluid.amount)));
        return outputs.snapshotOutputs();
    }
}
