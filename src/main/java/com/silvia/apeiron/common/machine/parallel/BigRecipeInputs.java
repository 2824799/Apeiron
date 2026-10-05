package com.silvia.apeiron.common.machine.parallel;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;

/** Exact recipe matching over the common inventory snapshot and transaction. */
public final class BigRecipeInputs {

    private final BigRecipeInventory inventory;
    private final List<IAEStack<?>> stocks;
    private final BigInputAllocation allocation;

    public BigRecipeInputs(MTEMultiBlockBase machine, GTRecipe recipe, ItemStack[] items, FluidStack[] fluids) {
        this(machine, null, recipe, items, fluids);
    }

    public BigRecipeInputs(MTEMultiBlockBase machine, MTEMultiBlockBase sharedInputs, GTRecipe recipe,
        ItemStack[] items, FluidStack[] fluids) {
        inventory = new BigRecipeInventory(machine, sharedInputs, items, fluids);
        stocks = inventory.getStacksBig();
        List<BigInteger> costs = new ArrayList<>();
        List<boolean[]> matches = new ArrayList<>();
        for (GTRecipe.RecipeItemInput input : recipe.getCachedCombinedItemInputs()) {
            if (input.inputAmount == 0) {
                boolean present = false;
                for (ItemStack item : items)
                    if (item != null && input.matchesRecipe(GTOreDictUnificator.getAssociation(item), item))
                        present = true;
                if (!present) throw new IllegalArgumentException("Missing catalyst");
                continue;
            }
            boolean[] matching = new boolean[stocks.size()];
            for (int i = 0; i < stocks.size(); i++) if (stocks.get(i) instanceof IAEItemStack) {
                ItemStack type = ((IAEItemStack) stocks.get(i)).getItemStack();
                matching[i] = input.matchesRecipe(GTOreDictUnificator.getAssociation(type), type);
            }
            costs.add(BigInteger.valueOf(input.inputAmount));
            matches.add(matching);
        }
        for (FluidStack input : recipe.mFluidInputs) if (input != null && input.amount > 0) {
            boolean[] matching = new boolean[stocks.size()];
            for (int i = 0; i < stocks.size(); i++) if (stocks.get(i) instanceof IAEFluidStack)
                matching[i] = ((IAEFluidStack) stocks.get(i)).getFluidStack()
                    .isFluidEqual(input);
            costs.add(BigInteger.valueOf(input.amount));
            matches.add(matching);
        }
        BigInteger[] available = stocks.stream()
            .map(BigAEStackValues::get)
            .toArray(BigInteger[]::new);
        allocation = new BigInputAllocation(
            available,
            costs.toArray(new BigInteger[0]),
            matches.toArray(new boolean[0][]),
            renewable());
    }

    private boolean[] renewable() {
        boolean[] result = new boolean[stocks.size()];
        for (int i = 0; i < result.length; i++) result[i] = BigAEStackValues.isInfinite(stocks.get(i));
        return result;
    }

    public BigInputAllocation allocation() {
        return allocation;
    }

    public void consume(BigInteger parallels) {
        BigInteger[] debit = allocation.allocate(parallels);
        if (debit == null) throw new IllegalStateException("Recipe snapshot cannot satisfy its plan");
        inventory.consume(debit);
    }
}
