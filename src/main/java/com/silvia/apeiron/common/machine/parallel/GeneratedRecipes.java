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
import com.silvia.apeiron.api.machine.parallel.GeneratedRecipeSource;
import com.silvia.apeiron.api.machine.parallel.ParallelLimit;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.output.BigRecipeOutputCapacity;
import com.silvia.apeiron.math.RecipeDisplayNumbers;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.IDualInputInventory;

/** Shared transaction for generators which bypass ProcessingLogic and have fixed cycle costs. */
public final class GeneratedRecipes {

    private static final Map<RecipeMap<?>, GeneratedRecipeSource> SOURCES = new IdentityHashMap<>();

    private GeneratedRecipes() {}

    public static void register(RecipeMap<?> map, GeneratedRecipeSource source) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(source, "source");
        if (SOURCES.containsKey(map)) throw new IllegalArgumentException("Generator source already registered");
        SOURCES.put(map, source);
    }

    /** Null retains the original controller processor whenever the source or energy hatch is absent. */
    public static CheckRecipeResult process(MTEMultiBlockBase machine) {
        GeneratedRecipeSource source = SOURCES.get(machine.getRecipeMap());
        MTEInfiniteEnergyHatch hatch = source == null ? null : InfiniteEnergyHatches.find(machine);
        if (hatch == null || !(machine instanceof BigWirelessController)) return null;
        if (((BigWirelessController) machine).getWirelessRecipeState()
            .isRunning()) return CheckRecipeResultRegistry.NO_RECIPE;
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
            machine.getStoredFluids()
                .toArray(new FluidStack[0]));
        return found == CheckRecipeResultRegistry.NO_RECIPE ? result : found;
    }

    private static CheckRecipeResult processInventory(MTEMultiBlockBase machine, MTEInfiniteEnergyHatch hatch,
        GeneratedRecipeSource source, ItemStack[] items, FluidStack[] fluids) {
        List<IAEStack<?>> stocks = new BigRecipeInventory(machine, items, fluids).getStacksBig();
        GTRecipe recipe = source.findRecipe(stocks);
        if (recipe == null) return CheckRecipeResultRegistry.NO_RECIPE;
        BigInteger fixedEUt = source.getFixedEUt(recipe);
        if (recipe.mDuration < 1 || recipe.mEUt < 1 || fixedEUt.signum() < 0)
            throw new IllegalArgumentException("Invalid fixed-cycle recipe cost");
        BigInteger perEUt = BigInteger.valueOf(recipe.mEUt);
        int efficiency = hatch.isUltimate() ? 10000
            : 10000 - (machine.getIdealStatus() - machine.getRepairStatus()) * 1000;
        BigInteger availableEUt = hatch.getAvailableEUBig()
            .divide(BigInteger.valueOf(recipe.mDuration))
            .multiply(BigInteger.valueOf(Math.max(1000, efficiency)))
            .divide(BigInteger.valueOf(10000));
        if (!hatch.isUltimate()) {
            BigInteger voltage = BigInteger.valueOf(InfiniteEnergyHatches.processingVoltage(machine));
            if (voltage.compareTo(perEUt.add(fixedEUt)) < 0) return CheckRecipeResultRegistry.insufficientPower(
                perEUt.add(fixedEUt)
                    .min(BigInteger.valueOf(Long.MAX_VALUE))
                    .longValue());
            availableEUt = availableEUt.min(voltage);
        }
        BigInteger affordable = availableEUt.subtract(fixedEUt)
            .max(BigInteger.ZERO)
            .divide(perEUt);
        ParallelLimit limit = ParallelLimit.bounded(
            ((BigWirelessController) machine).getParallelLimitBig()
                .applyTo(affordable));
        BigRecipeInputs inputs = new BigRecipeInputs(machine, recipe, items, fluids);
        BigInteger parallels = inputs.allocation()
            .maximum(limit);
        if (parallels.signum() == 0) return affordable.signum()
            == 0 ? CheckRecipeResultRegistry.insufficientPower(perEUt.add(fixedEUt)
                .multiply(BigInteger.valueOf(recipe.mDuration))
                .min(BigInteger.valueOf(Long.MAX_VALUE))
                .longValue()) : CheckRecipeResultRegistry.NO_RECIPE;
        BigMachineOutputQueue rolled = source.rollOutputs(recipe);
        if (!fits(machine, rolled, parallels)) {
            BigInteger lower = BigInteger.ZERO, upper = parallels;
            while (lower.compareTo(upper) < 0) {
                BigInteger middle = lower.add(upper)
                    .add(BigInteger.ONE)
                    .shiftRight(1);
                if (fits(machine, rolled, middle)) lower = middle;
                else upper = middle.subtract(BigInteger.ONE);
            }
            parallels = lower;
            if (parallels.signum() == 0) return BigRecipeOutputCapacity.check(machine, rolled.snapshotOutputs());
        }
        BigInteger total = RecipeDisplayNumbers.effectiveEUt(
            perEUt.multiply(parallels)
                .add(fixedEUt),
            efficiency)
            .multiply(BigInteger.valueOf(recipe.mDuration));
        int duration = hatch.isUltimate() ? InfiniteEnergyHatches.targetDuration(machine) : recipe.mDuration;
        BigMachineOutputQueue outputs = scale(rolled, parallels);
        inputs.consume(parallels);
        ((BigWirelessController) machine).getWirelessRecipeState()
            .startExact(parallels, total, duration, outputs, hatch.isUltimate());
        machine.mEfficiency = machine.mEfficiencyIncrease = 10000;
        machine.mMaxProgresstime = duration;
        machine.mEUt = 0;
        if (machine instanceof MTEExtendedPowerMultiBlockBase) ((MTEExtendedPowerMultiBlockBase) machine).lEUt = 0;
        machine.mOutputItems = null;
        machine.mOutputFluids = null;
        machine.updateSlots();
        machine.markDirty();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private static boolean fits(MTEMultiBlockBase machine, BigMachineOutputQueue rolled, BigInteger count) {
        return BigRecipeOutputCapacity.fits(machine, scale(rolled, count).snapshotOutputs());
    }

    private static BigMachineOutputQueue scale(BigMachineOutputQueue rolled, BigInteger count) {
        BigMachineOutputQueue result = new BigMachineOutputQueue();
        for (IAEStack<?> stack : rolled.snapshotOutputs()) {
            BigInteger amount = BigAEStackValues.get(stack)
                .multiply(count);
            if (stack instanceof IAEItemStack) result.addItem(((IAEItemStack) stack).getItemStack(), amount);
            else result.addFluid(((IAEFluidStack) stack).getFluidStack(), amount);
        }
        return result;
    }
}
