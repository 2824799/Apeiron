package com.silvia.apeiron.common.machine.parallel;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.ParallelLimit;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.math.RecipeOutputCounts;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.objects.XSTR;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.ParallelHelper;

/** One native overclock calculation, one exact input allocation, and one output ledger per recipe. */
public final class BigRecipeParallelHelper extends ParallelHelper
    implements com.silvia.apeiron.api.machine.parallel.PreparedWirelessRecipe {

    private final MTEMultiBlockBase controller;
    private final MTEInfiniteEnergyHatch hatch;
    private final WirelessRecipeState state;
    private BigRecipeInputs inputs;
    private BigInteger parallels;
    private BigInteger euPerParallel;
    private BigInteger totalPerParallel;
    private BigInteger totalEnergy;
    private java.util.function.Function<BigInteger, List<IAEStack<?>>> outputCalculator;

    public BigRecipeParallelHelper(MTEMultiBlockBase controller, MTEInfiniteEnergyHatch hatch,
        WirelessRecipeState state) {
        this.controller = controller;
        this.hatch = hatch;
        this.state = state;
    }

    public static boolean supports(GTRecipe recipe) {
        if (!supportsInputs(recipe)) return false;
        for (int i = 0; i < recipe.mOutputs.length; i++) if (recipe.getOutputChance(i) < 0) return false;
        for (int i = 0; i < recipe.mFluidOutputs.length; i++) if (recipe.getFluidOutputChance(i) < 0) return false;
        return true;
    }

    public static boolean supportsInputs(GTRecipe recipe) {
        if (recipe.mAltFluidInputs != null) return false;
        for (int i = 0; i < recipe.mInputs.length; i++) if (recipe.getInputChance(i) != 10000) return false;
        for (int i = 0; i < recipe.mFluidInputs.length; i++) if (recipe.getFluidInputChance(i) != 10000) return false;
        return true;
    }

    public BigRecipeParallelHelper setExactOutputCalculator(
        java.util.function.Function<BigInteger, List<IAEStack<?>>> calculator) {
        outputCalculator = calculator;
        return this;
    }

    @Override
    protected void determineParallel() {
        calculator.setEUt(com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches.processingVoltage(controller));
        if (!com.silvia.apeiron.compat.OverclockPolicies.allows(calculator, recipe.mEUt)) {
            result = CheckRecipeResultRegistry.insufficientVoltage(recipe.mEUt);
            return;
        }
        calculator.setParallel(1)
            .setCurrentParallel(1)
            .setAmperage(1)
            .setAmperageOC(false)
            .calculate();
        if (calculator.getConsumption() == Long.MAX_VALUE || calculator.getDuration() == Integer.MAX_VALUE) {
            result = CheckRecipeResultRegistry.POWER_OVERFLOW;
            return;
        }
        euPerParallel = BigInteger.valueOf(calculator.getConsumption());
        inputs = new BigRecipeInputs(controller, recipe, itemInputs, fluidInputs);
        plan(hatch.isUltimate() ? state.getTargetDuration() : Math.max(1, calculator.getDuration()));
    }

    public boolean plan(int duration) {
        int efficiency = 10000 - (controller.getIdealStatus() - controller.getRepairStatus()) * 1000;
        totalPerParallel = hatch.isUltimate() ? BigInteger.valueOf(recipe.mEUt)
            .multiply(BigInteger.valueOf(recipe.mDuration)) : euPerParallel.multiply(BigInteger.valueOf(duration));
        ParallelLimit cap = state.getLimit();
        if (totalPerParallel.signum() > 0) {
            BigInteger affordable = hatch.isUltimate()
                ? com.silvia.apeiron.math.RecipeEnergyBudget.affordable(hatch.getAvailableEUBig(), totalPerParallel)
                : com.silvia.apeiron.math.RecipeEnergyBudget
                    .affordableNormal(hatch.getAvailableEUBig(), euPerParallel, duration, efficiency);
            cap = ParallelLimit.bounded(cap.applyTo(affordable));
        }
        parallels = inputs.allocation()
            .maximum(cap);
        if (parallels.signum() <= 0) {
            result = CheckRecipeResultRegistry.NO_RECIPE;
            return false;
        }
        if (!outputsFit(parallels)) {
            BigInteger upper = parallels, lower = BigInteger.ZERO;
            while (lower.compareTo(upper) < 0) {
                BigInteger middle = lower.add(upper)
                    .add(BigInteger.ONE)
                    .shiftRight(1);
                if (outputsFit(middle)) lower = middle;
                else upper = middle.subtract(BigInteger.ONE);
            }
            parallels = lower;
            if (parallels.signum() == 0) {
                result = outputResult(BigInteger.ONE);
                return false;
            }
        }
        totalEnergy = hatch.isUltimate() ? totalPerParallel.multiply(parallels)
            : com.silvia.apeiron.math.RecipeDisplayNumbers.effectiveEUt(euPerParallel.multiply(parallels), efficiency)
                .multiply(BigInteger.valueOf(duration));
        // Only the native single-recipe OC/hook uses this compatibility view; no big output arrays are materialized.
        currentParallel = 1;
        itemOutputs = new ItemStack[0];
        fluidOutputs = new FluidStack[0];
        result = CheckRecipeResultRegistry.SUCCESSFUL;
        return true;
    }

    private List<IAEStack<?>> outputs(BigInteger count, boolean capacity) {
        if (outputCalculator != null) return outputCalculator.apply(count);
        List<IAEStack<?>> values = new ArrayList<>();
        for (int i = 0; i < Math.min(recipe.mOutputs.length, controller.getItemOutputLimit()); i++)
            if (recipe.mOutputs[i] != null && recipe.getOutputChance(i) > 0) values.add(
                BigAEStackValues.copyWithSize(
                    AEItemStack.create(recipe.mOutputs[i]),
                    outputCount(count, recipe.getOutputChance(i), capacity)
                        .multiply(BigInteger.valueOf(recipe.mOutputs[i].stackSize))));
        for (int i = 0; i < Math.min(recipe.mFluidOutputs.length, controller.getFluidOutputLimit()); i++)
            if (recipe.mFluidOutputs[i] != null && recipe.getFluidOutputChance(i) > 0) values.add(
                BigAEStackValues.copyWithSize(
                    AEFluidStack.create(recipe.mFluidOutputs[i]),
                    outputCount(count, recipe.getFluidOutputChance(i), capacity)
                        .multiply(BigInteger.valueOf(recipe.mFluidOutputs[i].amount))));
        return values;
    }

    private static BigInteger outputCount(BigInteger count, int chance, boolean capacity) {
        return capacity ? RecipeOutputCounts.maximum(count, chance)
            : RecipeOutputCounts.roll(count, chance, XSTR.XSTR_INSTANCE);
    }

    private boolean outputsFit(BigInteger count) {
        return outputResult(count).wasSuccessful();
    }

    private CheckRecipeResult outputResult(BigInteger count) {
        return com.silvia.apeiron.common.machine.output.BigRecipeOutputCapacity.check(controller, outputs(count, true));
    }

    public void commit(int duration) {
        if (state.isRunning() || duration < 1) throw new IllegalStateException("Wireless controller already running");
        // Capacity planning and repeated native duration calculations never draw random outputs.
        BigMachineOutputQueue exactOutputs = new BigMachineOutputQueue();
        for (IAEStack<?> output : outputs(parallels, false)) {
            if (output instanceof IAEItemStack)
                exactOutputs.addItem(((IAEItemStack) output).getItemStack(), BigAEStackValues.get(output));
            else exactOutputs.addFluid(((IAEFluidStack) output).getFluidStack(), BigAEStackValues.get(output));
        }
        inputs.consume(parallels);
        state.startExact(parallels, totalEnergy, duration, exactOutputs, hatch.isUltimate());
    }

    public BigInteger getParallelsBig() {
        return parallels;
    }

    public int recipeDuration(int nativeDuration) {
        return hatch.isUltimate() ? state.getTargetDuration() : Math.max(1, nativeDuration);
    }

    public BigInteger getTotalEnergyBig() {
        return totalEnergy;
    }
}
