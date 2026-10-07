package com.silvia.apeiron.common.integration.lanthanides;

import java.lang.reflect.Field;
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

/** Compatibility replacement for the obsolete optional EyeOfHarmonyBuffer target-chamber processor. */
public final class TargetChamberEnhancement {

    public static final int MAX_PARALLEL = 2050781;
    public static final int DURATION = 20;
    private static boolean resolved;
    private static Field enabledField;

    private TargetChamberEnhancement() {}

    public static boolean isEnabled() {
        if (!resolved) {
            resolved = true;
            try {
                enabledField = Class.forName("com.EyeOfHarmonyBuffer.Config.MainConfig")
                    .getField("TargetChamberEnable");
            } catch (ClassNotFoundException | NoSuchFieldException ignored) {}
        }
        if (enabledField == null) return false;
        try {
            return enabledField.getBoolean(null);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot read target chamber enhancement setting", error);
        }
    }

    public static CheckRecipeResult process(MTEExtendedPowerMultiBlockBase machine, ItemStack[] items) {
        if (((BigWirelessController) machine).getWirelessRecipeState()
            .isRunning()) return CheckRecipeResultRegistry.NO_RECIPE;
        // Mirrors may expose the same mutable slot through both input roles.
        java.util.Set<ItemStack> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        items = java.util.Arrays.stream(items)
            .filter(java.util.Objects::nonNull)
            .filter(seen::add)
            .toArray(ItemStack[]::new);
        long voltage = GTValues.VP[(int) machine.getInputVoltageTier()];
        GTRecipe recipe = LanthanidesRecipeMaps.targetChamberRecipes.findRecipeQuery()
            .items(items)
            .voltage(voltage)
            .filter(r -> r.getMetadata(LanthanidesRecipeMaps.TARGET_CHAMBER_METADATA) != null)
            .find();
        if (recipe == null) return CheckRecipeResultRegistry.NO_RECIPE;
        // All entries are the mutable native views, including masks in the dedicated front bus.
        // Bounding by those views keeps native consumeInput safe and preserves each input session.
        int parallels = (int) Math.min(
            MAX_PARALLEL,
            recipe.maxParallelCalculatedByInputs(MAX_PARALLEL, GTValues.emptyFluidStackArray, items));
        if (parallels <= 0) return CheckRecipeResultRegistry.NO_RECIPE;
        BigMachineOutputQueue outputs = outputs(recipe, parallels);
        if (!BigRecipeOutputCapacity.fits(machine, outputs.snapshotOutputs())) {
            int low = 0, high = parallels;
            while (low < high) {
                int middle = low + (high - low + 1) / 2;
                if (BigRecipeOutputCapacity.fits(machine, outputs(recipe, middle).snapshotOutputs())) low = middle;
                else high = middle - 1;
            }
            if (low == 0) return BigRecipeOutputCapacity.check(machine, outputs(recipe, 1).snapshotOutputs());
            parallels = low;
            outputs = outputs(recipe, parallels);
        }
        recipe.consumeInput(parallels, GTValues.emptyFluidStackArray, items);
        machine.mEfficiency = machine.mEfficiencyIncrease = 10000;
        machine.mMaxProgresstime = DURATION;
        machine.lEUt = -voltage;
        machine.mOutputItems = null;
        machine.mOutputFluids = null;
        ((BigWirelessController) machine).getWirelessRecipeState()
            .startNativePowered(
                BigInteger.valueOf(parallels),
                BigInteger.valueOf(voltage)
                    .multiply(BigInteger.valueOf(DURATION)),
                DURATION,
                outputs);
        machine.updateSlots();
        machine.markDirty();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private static BigMachineOutputQueue outputs(GTRecipe recipe, int parallels) {
        BigMachineOutputQueue outputs = new BigMachineOutputQueue();
        for (ItemStack stack : recipe.mOutputs) if (stack != null && stack.stackSize > 0) outputs.addItem(
            stack,
            BigInteger.valueOf(stack.stackSize)
                .multiply(BigInteger.valueOf(parallels)));
        return outputs;
    }
}
