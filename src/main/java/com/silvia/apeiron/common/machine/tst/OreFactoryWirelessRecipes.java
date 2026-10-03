package com.silvia.apeiron.common.machine.tst;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;

import com.Nxer.TwistSpaceTechnology.common.api.giver.ItemStacksGiver;
import com.Nxer.TwistSpaceTechnology.system.OreProcess.logic.OP_Logic;
import com.Nxer.TwistSpaceTechnology.system.OreProcess.logic.OP_Values;
import com.Nxer.TwistSpaceTechnology.util.rewrites.TST_ItemID;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.util.GTRecipe;

/** Ore factors remain TST-owned; lubricant stays in the factory's periodic running callback. */
public final class OreFactoryWirelessRecipes {

    private static RecipeMap<?> recipes;
    private static final Set<TST_ItemID> imported = new HashSet<>();

    private OreFactoryWirelessRecipes() {}

    public static RecipeMap<?> get() {
        if (recipes == null) recipes = RecipeMapBuilder.of("apeiron.wireless_ore_factory")
            .maxIO(1, 128, 0, 0)
            .build();
        if (imported.size() != OP_Logic.OP_GIVER_MAP.size()) {
            for (Map.Entry<TST_ItemID, ItemStacksGiver> entry : OP_Logic.OP_GIVER_MAP.entrySet()) {
                if (imported.contains(entry.getKey())) continue;
                ItemStack[] outputs = entry.getValue().cache.entrySet()
                    .stream()
                    .map(
                        e -> e.getKey()
                            .getItemStack(Math.toIntExact(e.getValue())))
                    .toArray(ItemStack[]::new);
                if (outputs.length == 0) continue;
                recipes.addRecipe(
                    new GTRecipe(
                        false,
                        new ItemStack[] { entry.getKey()
                            .getItemStack(1) },
                        outputs,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        OP_Values.OreProcessRecipeDuration,
                        OP_Values.OreProcessRecipeEUt,
                        0));
                imported.add(entry.getKey());
            }
        }
        return recipes;
    }
}
