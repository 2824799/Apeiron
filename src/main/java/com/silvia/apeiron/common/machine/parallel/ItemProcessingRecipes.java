package com.silvia.apeiron.common.machine.parallel;

import java.math.BigInteger;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.api.machine.parallel.ItemProcessingRecipe;
import com.silvia.apeiron.api.machine.parallel.ItemProcessingRecipeSource;
import com.silvia.apeiron.api.machine.parallel.ParallelLimit;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.output.BigRecipeOutputCapacity;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.IDualInputInventory;

/** Sources register recipe data; every matching controller uses the same input, energy and output transaction. */
public final class ItemProcessingRecipes {

    private static final Map<RecipeMap<?>, ItemProcessingRecipeSource> SOURCES = new IdentityHashMap<>();

    private ItemProcessingRecipes() {}

    public static void register(RecipeMap<?> recipeMap, ItemProcessingRecipeSource source) {
        Objects.requireNonNull(recipeMap, "recipeMap");
        Objects.requireNonNull(source, "source");
        if (source.getDurationTicks() < 1) throw new IllegalArgumentException("Invalid transformation duration");
        if (SOURCES.containsKey(recipeMap)) throw new IllegalArgumentException("Recipe source already registered");
        SOURCES.put(recipeMap, source);
    }

    public static boolean hasSource(RecipeMap<?> recipeMap) {
        return SOURCES.containsKey(recipeMap);
    }

    /** Called inside GT's start/end recipe-processing bracket. Null delegates to the native controller. */
    public static CheckRecipeResult process(MTEMultiBlockBase machine) {
        ItemProcessingRecipeSource source = SOURCES.get(machine.getRecipeMap());
        MTEInfiniteEnergyHatch hatch = source == null ? null : InfiniteEnergyHatches.find(machine);
        if (hatch == null || !(machine instanceof BigWirelessController)) return null;
        WirelessRecipeState state = ((BigWirelessController) machine).getWirelessRecipeState();
        if (state.isRunning()) return CheckRecipeResultRegistry.NO_RECIPE;
        CheckRecipeResult validation = source.validate(machine);
        if (!validation.wasSuccessful()) return validation;
        CheckRecipeResult result = CheckRecipeResultRegistry.NO_RECIPE;
        Set<IDualInputInventory> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (IDualInputHatch input : machine.mDualInputHatches) {
            for (java.util.Iterator<? extends IDualInputInventory> it = input.inventories(); it.hasNext();) {
                IDualInputInventory inventory = it.next();
                if (!seen.add(inventory) || inventory.isEmpty()) continue;
                CheckRecipeResult found = processInventory(
                    machine,
                    hatch,
                    source,
                    inventory.getItemInputs(),
                    inventory.getFluidInputs());
                if (found.wasSuccessful()) return found;
                if (found != CheckRecipeResultRegistry.NO_RECIPE) result = found;
            }
        }
        CheckRecipeResult found = processInventory(
            machine,
            hatch,
            source,
            machine.getStoredInputs()
                .toArray(new ItemStack[0]),
            new FluidStack[0]);
        return found == CheckRecipeResultRegistry.NO_RECIPE ? result : found;
    }

    private static CheckRecipeResult processInventory(MTEMultiBlockBase machine, MTEInfiniteEnergyHatch hatch,
        ItemProcessingRecipeSource source, ItemStack[] items, FluidStack[] fluids) {
        BigRecipeInventory inventory = new BigRecipeInventory(machine, items, fluids);
        List<IAEStack<?>> stocks = inventory.getStacksBig();
        ItemProcessingRecipe[] recipes = new ItemProcessingRecipe[stocks.size()];
        BigInteger[] available = new BigInteger[stocks.size()], costs = new BigInteger[stocks.size()];
        boolean[] renewable = new boolean[stocks.size()];
        WirelessRecipeState state = ((BigWirelessController) machine).getWirelessRecipeState();
        ParallelLimit parallelLimit = ((BigWirelessController) machine).getParallelLimitBig();
        boolean any = false;
        BigInteger minimumCost = null;
        for (int i = 0; i < stocks.size(); i++) {
            IAEStack<?> stock = stocks.get(i);
            available[i] = BigAEStackValues.get(stock);
            renewable[i] = BigAEStackValues.isInfinite(stock);
            if (!(stock instanceof IAEItemStack)) continue;
            ItemStack type = ((IAEItemStack) stock).getItemStack();
            type.stackSize = 1;
            recipes[i] = source.findRecipe(type);
            if (recipes[i] == null && source.returnsUnmatchedItems()) {
                BigMachineOutputQueue returned = new BigMachineOutputQueue();
                returned.addItem(type, BigInteger.ONE);
                recipes[i] = new ItemProcessingRecipe(BigInteger.ZERO, returned);
            }
            if (recipes[i] == null) continue;
            costs[i] = recipes[i].getTotalEU();
            if (renewable[i] && costs[i].signum() == 0 && parallelLimit.isUnlimited())
                return CheckRecipeResultRegistry.NO_RECIPE;
            minimumCost = minimumCost == null ? costs[i] : minimumCost.min(costs[i]);
            any = true;
        }
        if (!any) return CheckRecipeResultRegistry.NO_RECIPE;
        ItemProcessingPlan plan = ItemProcessingPlan.calculate(
            available,
            renewable,
            costs,
            parallelLimit,
            hatch.getAvailableEUBig(),
            debit -> BigRecipeOutputCapacity.fits(machine, outputs(recipes, debit).snapshotOutputs()));
        if (plan.getParallelsBig()
            .signum() <= 0) {
            if (minimumCost.compareTo(hatch.getAvailableEUBig()) > 0)
                return CheckRecipeResultRegistry.insufficientPower(
                    minimumCost.min(BigInteger.valueOf(Long.MAX_VALUE))
                        .longValue());
            return CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;
        }
        int duration = hatch.isUltimate() ? InfiniteEnergyHatches.targetDuration(machine) : source.getDurationTicks();
        BigMachineOutputQueue output = outputs(recipes, plan.getDebits());
        inventory.consume(plan.getDebits());
        state.startExact(plan.getParallelsBig(), plan.getTotalEUBig(), duration, output, hatch.isUltimate());
        machine.mEfficiency = 10000;
        machine.mEfficiencyIncrease = 10000;
        machine.mMaxProgresstime = duration;
        machine.mEUt = 0;
        if (machine instanceof MTEExtendedPowerMultiBlockBase) ((MTEExtendedPowerMultiBlockBase) machine).lEUt = 0;
        machine.mOutputItems = null;
        machine.mOutputFluids = null;
        machine.updateSlots();
        machine.markDirty();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private static BigMachineOutputQueue outputs(ItemProcessingRecipe[] recipes, BigInteger[] debit) {
        BigMachineOutputQueue result = new BigMachineOutputQueue();
        for (int i = 0; i < recipes.length; i++) {
            if (recipes[i] == null || debit[i].signum() == 0) continue;
            for (IAEStack<?> output : recipes[i].getOutputs()) {
                BigInteger amount = BigAEStackValues.get(output)
                    .multiply(debit[i]);
                if (output instanceof IAEItemStack) result.addItem(((IAEItemStack) output).getItemStack(), amount);
                else result.addFluid(((IAEFluidStack) output).getFluidStack(), amount);
            }
        }
        return result;
    }
}
