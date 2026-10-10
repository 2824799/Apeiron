package com.silvia.apeiron.common.integration.lanthanides;

import java.math.BigInteger;

import net.minecraft.item.ItemStack;

import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.output.BigRecipeOutputCapacity;

import gregtech.api.enums.GTValues;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gtnhlanth.api.recipe.LanthanidesRecipeMaps;
import gtnhlanth.common.register.LanthItemList;
import gtnhlanth.common.tileentity.recipe.beamline.TargetChamberMetadata;

/** Item-particle execution for target chambers fitted with an Apeiron pattern input. */
public final class TargetChamberEnhancement {

    private TargetChamberEnhancement() {}

    public static CheckRecipeResult processParticleItems(MTEExtendedPowerMultiBlockBase machine, ItemStack[] items) {
        if (((BigWirelessController) machine).getWirelessRecipeState()
            .isRunning()) return CheckRecipeResultRegistry.NO_RECIPE;
        // Mirrors may expose the same mutable slot through both input roles.
        java.util.Set<ItemStack> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        items = java.util.Arrays.stream(items)
            .filter(java.util.Objects::nonNull)
            .filter(seen::add)
            .toArray(ItemStack[]::new);
        final ItemStack[] supplied = items;
        long voltage = GTValues.VP[(int) machine.getInputVoltageTier()];
        GTRecipe recipe = LanthanidesRecipeMaps.targetChamberRecipes.findRecipeQuery()
            .items(items)
            .voltage(voltage)
            .filter(r -> {
                TargetChamberMetadata metadata = r.getMetadata(LanthanidesRecipeMaps.TARGET_CHAMBER_METADATA);
                return metadata != null && particleInputs(r, metadata)
                    .maxParallelCalculatedByInputs(1, GTValues.emptyFluidStackArray, supplied) >= 1;
            })
            .find();
        if (recipe == null) return CheckRecipeResultRegistry.NO_RECIPE;
        GTRecipe inputRecipe = particleInputs(
            recipe,
            recipe.getMetadata(LanthanidesRecipeMaps.TARGET_CHAMBER_METADATA));
        GTRecipe outputRecipe = com.silvia.apeiron.common.machine.parallel.RecipeChanceEffects
            .apply(machine, recipe, 1);
        // All entries are the mutable native views, including masks in the dedicated front bus.
        // Bounding by those views keeps native consumeInput safe and preserves each input session.
        int maxParallel = Integer.MAX_VALUE;
        int duration = 1;
        int parallels = (int) Math.min(
            maxParallel,
            inputRecipe.maxParallelCalculatedByInputs(maxParallel, GTValues.emptyFluidStackArray, items));
        if (parallels <= 0) return CheckRecipeResultRegistry.NO_RECIPE;
        BigMachineOutputQueue outputs = outputs(outputRecipe, parallels, false);
        if (!BigRecipeOutputCapacity.fits(machine, outputs.snapshotOutputsUnsorted())) {
            int low = 0, high = parallels;
            while (low < high) {
                int middle = low + (int) (((long) high - low + 1) / 2);
                if (BigRecipeOutputCapacity
                    .fits(machine, outputs(outputRecipe, middle, false).snapshotOutputsUnsorted())) low = middle;
                else high = middle - 1;
            }
            if (low == 0) return BigRecipeOutputCapacity
                .check(machine, outputs(outputRecipe, 1, false).snapshotOutputsUnsorted());
            parallels = low;
            outputs = outputs(outputRecipe, parallels, false);
        }
        outputs = outputs(outputRecipe, parallels, true);
        inputRecipe.consumeInput(parallels, GTValues.emptyFluidStackArray, items);
        machine.mEfficiency = 10000 - (machine.getIdealStatus() - machine.getRepairStatus()) * 1000;
        machine.mEfficiencyIncrease = 10000;
        machine.mMaxProgresstime = duration;
        machine.lEUt = -voltage;
        machine.mOutputItems = null;
        machine.mOutputFluids = null;
        ((BigWirelessController) machine).getWirelessRecipeState()
            .startNativePowered(
                BigInteger.valueOf(parallels),
                BigInteger.valueOf(voltage)
                    .multiply(BigInteger.valueOf(duration)),
                duration,
                outputs);
        machine.updateSlots();
        machine.markDirty();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    /** Include particle counts in the same native match/debit as materials, without modifying the registered recipe. */
    private static GTRecipe particleInputs(GTRecipe recipe, TargetChamberMetadata metadata) {
        if (metadata.amount == 0) return recipe;
        GTRecipe inputRecipe = recipe.copy();
        inputRecipe.mInputs = java.util.Arrays.copyOf(inputRecipe.mInputs, inputRecipe.mInputs.length + 1);
        inputRecipe.mInputs[inputRecipe.mInputs.length
            - 1] = new ItemStack(LanthItemList.PARTICLE_ITEM, metadata.amount, metadata.particleID);
        return inputRecipe;
    }

    private static BigMachineOutputQueue outputs(GTRecipe recipe, int parallels, boolean roll) {
        BigMachineOutputQueue outputs = new BigMachineOutputQueue();
        for (int i = 0; i < recipe.mOutputs.length; i++) {
            ItemStack stack = recipe.mOutputs[i];
            if (stack == null || stack.stackSize <= 0) continue;
            BigInteger count = roll ? com.silvia.apeiron.math.RecipeOutputCounts
                .roll(BigInteger.valueOf(parallels), recipe.getOutputChance(i), gregtech.api.objects.XSTR.XSTR_INSTANCE)
                : com.silvia.apeiron.math.RecipeOutputCounts
                    .maximum(BigInteger.valueOf(parallels), recipe.getOutputChance(i));
            outputs.addItem(stack, count.multiply(BigInteger.valueOf(stack.stackSize)));
        }
        return outputs;
    }
}
