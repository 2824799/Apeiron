package com.silvia.apeiron.common.integration.tst.parallel;

import java.math.BigInteger;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.Nxer.TwistSpaceTechnology.common.api.giver.ItemStacksGiver;
import com.Nxer.TwistSpaceTechnology.common.recipeMap.GTCMRecipe;
import com.Nxer.TwistSpaceTechnology.system.OreProcess.logic.OP_Logic;
import com.Nxer.TwistSpaceTechnology.system.OreProcess.logic.OP_Values;
import com.Nxer.TwistSpaceTechnology.util.rewrites.TST_ItemID;
import com.silvia.apeiron.api.machine.parallel.ItemProcessingRecipe;
import com.silvia.apeiron.api.machine.parallel.ItemProcessingRecipeSource;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.parallel.ItemProcessingRecipes;

/** Exposes TST's live transformation data without rebuilding maps or owning a controller execution path. */
public final class TstItemProcessingRecipes implements ItemProcessingRecipeSource {

    private TstItemProcessingRecipes() {}

    public static void register() {
        ItemProcessingRecipes.register(GTCMRecipe.OreProcessingVisualRecipeMap, new TstItemProcessingRecipes());
    }

    @Override
    public int getDurationTicks() {
        return OP_Values.OreProcessRecipeDuration;
    }

    @Override
    public boolean returnsUnmatchedItems() {
        return OP_Values.moveUnprocessedItemsToOutputs;
    }

    @Override
    public ItemProcessingRecipe findRecipe(ItemStack input) {
        ItemStacksGiver giver = OP_Logic.getOutput(input);
        if (giver == null) return null;
        BigMachineOutputQueue outputs = new BigMachineOutputQueue();
        for (Map.Entry<TST_ItemID, Long> entry : giver.cache.entrySet()) outputs.addItem(
            entry.getKey()
                .getItemStack(1),
            BigInteger.valueOf(entry.getValue()));
        return new ItemProcessingRecipe(
            BigInteger.valueOf(OP_Values.OreProcessRecipeEUt)
                .multiply(BigInteger.valueOf(getDurationTicks())),
            outputs);
    }
}
