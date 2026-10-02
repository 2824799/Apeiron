package com.silvia.apeiron.common.machine.parallel;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.me.output.BigFluidOutputTransaction;
import com.silvia.apeiron.api.machine.me.output.BigItemOutputTransaction;
import com.silvia.apeiron.api.machine.parallel.ParallelLimit;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.interfaces.IOutputBus;
import gregtech.api.interfaces.IOutputBusTransaction;
import gregtech.api.interfaces.IOutputHatch;
import gregtech.api.interfaces.IOutputHatchTransaction;
import gregtech.api.interfaces.IOutputTransaction;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.ParallelHelper;

/** One native overclock calculation, one exact input allocation, and one output ledger per recipe. */
public final class BigRecipeParallelHelper extends ParallelHelper {

    private final MTEMultiBlockBase controller;
    private final MTEInfiniteEnergyHatch hatch;
    private final WirelessRecipeState state;
    private BigRecipeInputs inputs;
    private BigInteger parallels;
    private BigInteger euPerParallel;
    private final BigMachineOutputQueue exactOutputs = new BigMachineOutputQueue();

    public BigRecipeParallelHelper(MTEMultiBlockBase controller, MTEInfiniteEnergyHatch hatch,
        WirelessRecipeState state) {
        this.controller = controller;
        this.hatch = hatch;
        this.state = state;
    }

    public static boolean supports(GTRecipe recipe) {
        if (recipe.mAltFluidInputs != null) return false;
        for (int i = 0; i < recipe.mInputs.length; i++) if (recipe.getInputChance(i) != 10000) return false;
        for (int i = 0; i < recipe.mFluidInputs.length; i++) if (recipe.getFluidInputChance(i) != 10000) return false;
        for (int i = 0; i < recipe.mOutputs.length; i++)
            if (recipe.getOutputChance(i) != 10000 && recipe.getOutputChance(i) != 0) return false;
        for (int i = 0; i < recipe.mFluidOutputs.length; i++)
            if (recipe.getFluidOutputChance(i) != 10000 && recipe.getFluidOutputChance(i) != 0) return false;
        return true;
    }

    @Override
    protected void determineParallel() {
        if (recipe.mEUt < 0 || recipe.mEUt > calculator.getMaxAllowedRecipeEUt()) {
            result = CheckRecipeResultRegistry.insufficientVoltage(recipe.mEUt);
            return;
        }
        calculator.setParallel(1)
            .setCurrentParallel(1)
            .setAmperage(1)
            .setEUt(hatch.maxEUInput())
            .setAmperageOC(false)
            .calculate();
        if (calculator.getConsumption() == Long.MAX_VALUE || calculator.getDuration() == Integer.MAX_VALUE) {
            result = CheckRecipeResultRegistry.POWER_OVERFLOW;
            return;
        }
        euPerParallel = BigInteger.valueOf(calculator.getConsumption());
        inputs = new BigRecipeInputs(controller, recipe, itemInputs, fluidInputs);
        ParallelLimit cap = state.getLimit();
        if (euPerParallel.signum() > 0) {
            BigInteger affordable = hatch.getAvailableEUBig()
                .divide(euPerParallel);
            cap = ParallelLimit.bounded(cap.applyTo(affordable));
        }
        parallels = inputs.allocation()
            .maximum(cap);
        if (parallels.signum() <= 0) {
            result = CheckRecipeResultRegistry.NO_RECIPE;
            return;
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
                result = CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;
                return;
            }
        }
        for (IAEStack<?> output : outputs(parallels)) {
            if (output instanceof IAEItemStack)
                exactOutputs.addItem(((IAEItemStack) output).getItemStack(), BigAEStackValues.get(output));
            else exactOutputs.addFluid(((IAEFluidStack) output).getFluidStack(), BigAEStackValues.get(output));
        }
        // Only the native single-recipe OC/hook uses this compatibility view; no big output arrays are materialized.
        currentParallel = 1;
        itemOutputs = new ItemStack[0];
        fluidOutputs = new FluidStack[0];
        result = CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private List<IAEStack<?>> outputs(BigInteger count) {
        List<IAEStack<?>> values = new ArrayList<>();
        for (int i = 0; i < Math.min(recipe.mOutputs.length, controller.getItemOutputLimit()); i++)
            if (recipe.mOutputs[i] != null && recipe.getOutputChance(i) > 0) values.add(
                BigAEStackValues.copyWithSize(
                    AEItemStack.create(recipe.mOutputs[i]),
                    count.multiply(BigInteger.valueOf(recipe.mOutputs[i].stackSize))));
        for (int i = 0; i < Math.min(recipe.mFluidOutputs.length, controller.getFluidOutputLimit()); i++)
            if (recipe.mFluidOutputs[i] != null && recipe.getFluidOutputChance(i) > 0) values.add(
                BigAEStackValues.copyWithSize(
                    AEFluidStack.create(recipe.mFluidOutputs[i]),
                    count.multiply(BigInteger.valueOf(recipe.mFluidOutputs[i].amount))));
        return values;
    }

    private boolean outputsFit(BigInteger count) {
        List<BigItemOutputTransaction> items = new ArrayList<>();
        List<BigFluidOutputTransaction> fluids = new ArrayList<>();
        for (IOutputBus bus : controller.getOutputBusses()) {
            IOutputBusTransaction tx = bus.createTransaction();
            if (tx instanceof BigItemOutputTransaction) {
                recipeCheck(tx);
                items.add((BigItemOutputTransaction) tx);
            }
        }
        for (IOutputHatch output : controller.getOutputHatches()) {
            IOutputHatchTransaction tx = output.createTransaction();
            if (tx instanceof BigFluidOutputTransaction) {
                recipeCheck(tx);
                fluids.add((BigFluidOutputTransaction) tx);
            }
        }
        for (IAEStack<?> output : outputs(count)) {
            if (output instanceof IAEItemStack && protectExcessItem) {
                for (BigItemOutputTransaction tx : items)
                    tx.storePartialBig((IAEItemStack) output, BigInteger.ONE, BigInteger.ONE);
                if (BigAEStackValues.get(output)
                    .signum() > 0) return false;
            } else if (output instanceof IAEFluidStack && protectExcessFluid) {
                for (BigFluidOutputTransaction tx : fluids)
                    tx.storePartialBig((IAEFluidStack) output, BigInteger.ONE, BigInteger.ONE);
                if (BigAEStackValues.get(output)
                    .signum() > 0) return false;
            }
        }
        return true;
    }

    private static void recipeCheck(IOutputTransaction<?, ?> tx) {
        if (tx instanceof IOutputTransaction.IRecipeCheckAware)
            ((IOutputTransaction.IRecipeCheckAware) tx).setRecipeCheck(true);
    }

    public void commit(int duration) {
        if (state.isRunning() || duration < 1) throw new IllegalStateException("Wireless controller already running");
        inputs.consume(parallels);
        state.start(parallels, euPerParallel.multiply(parallels), duration, exactOutputs);
    }

    public BigInteger getParallelsBig() {
        return parallels;
    }
}
