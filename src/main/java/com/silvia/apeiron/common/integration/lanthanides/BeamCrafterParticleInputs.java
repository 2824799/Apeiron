package com.silvia.apeiron.common.integration.lanthanides;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;
import gregtech.loaders.postload.recipes.beamcrafter.BeamCrafterMetadata;
import gtnhlanth.common.beamline.Particle;
import gtnhlanth.common.item.ItemParticle;
import gtnhlanth.common.register.LanthItemList;

/** Particle items are debited with the selected recipe, never pooled across pattern inventories. */
public final class BeamCrafterParticleInputs {

    private BeamCrafterParticleInputs() {}

    public static GTRecipe inputRecipe(GTRecipe recipe) {
        BeamCrafterMetadata metadata = recipe.getMetadata(RecipeMaps.BEAMCRAFTER_METADATA);
        if (metadata == null) return null;
        GTRecipe inputs = recipe.copy();
        inputs.mInputs = java.util.Arrays.copyOf(inputs.mInputs, inputs.mInputs.length + 2);
        inputs.mInputs[inputs.mInputs.length
            - 2] = new ItemStack(LanthItemList.PARTICLE_ITEM, metadata.amount_A, metadata.particleID_A);
        inputs.mInputs[inputs.mInputs.length
            - 1] = new ItemStack(LanthItemList.PARTICLE_ITEM, metadata.amount_B, metadata.particleID_B);
        return inputs;
    }

    private static boolean isParticle(ItemStack item) {
        return item != null && item.getItem() instanceof ItemParticle
            && item.getItemDamage() >= 0
            && item.getItemDamage() < Particle.VALUES.length;
    }

    public static double parallels(GTRecipe recipe, int maximum, FluidStack[] fluids, ItemStack[] items) {
        BeamCrafterMetadata metadata = recipe.getMetadata(RecipeMaps.BEAMCRAFTER_METADATA);
        if (metadata == null) return 0;
        long perRecipe = (long) metadata.amount_A + metadata.amount_B;
        int bounded = (int) Math.min(maximum, Integer.MAX_VALUE / perRecipe);
        double parallel = recipe.maxParallelCalculatedByInputs(bounded, fluids, items);
        if (metadata.particleID_A == metadata.particleID_B)
            return Math.min(parallel, count(items, metadata.particleID_A) / perRecipe);
        return Math.min(
            parallel,
            Math.min(
                count(items, metadata.particleID_A) / metadata.amount_A,
                count(items, metadata.particleID_B) / metadata.amount_B));
    }

    public static void consume(GTRecipe recipe, int parallels, FluidStack[] fluids, ItemStack[] items) {
        BeamCrafterMetadata metadata = recipe.getMetadata(RecipeMaps.BEAMCRAFTER_METADATA);
        recipe.consumeInput(parallels, fluids, items);
        debit(items, metadata.particleID_A, (long) metadata.amount_A * parallels);
        debit(items, metadata.particleID_B, (long) metadata.amount_B * parallels);
    }

    private static long count(ItemStack[] items, int id) {
        long result = 0;
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ItemStack item : items)
            if (isParticle(item) && item.getItemDamage() == id && seen.add(item)) result += Math.max(0, item.stackSize);
        return result;
    }

    private static void debit(ItemStack[] items, int id, long amount) {
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ItemStack item : items) {
            if (!isParticle(item) || item.getItemDamage() != id || !seen.add(item)) continue;
            int taken = (int) Math.min(amount, Math.max(0, item.stackSize));
            item.stackSize -= taken;
            amount -= taken;
            if (amount == 0) return;
        }
        if (amount != 0) throw new IllegalStateException("Beam particle inventory changed while committing recipe");
    }
}
