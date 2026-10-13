package com.silvia.apeiron.common.machine.parallel;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.api.machine.parallel.ParallelLimit;
import com.silvia.apeiron.common.integration.gregtech.energy.GTWirelessEnergyNetwork;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.output.BigRecipeOutputCapacity;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.ParallelHelper;

/** Exact inputs and products for native wireless recipes whose whole cost is independent of working time. */
public final class BigPrepaidParallelHelper extends ParallelHelper {

    private final MTEMultiBlockBase controller;
    private final UUID owner;
    private final ParallelLimit limit;
    private final BigInteger unitEnergy;
    private BigRecipeInputs inputs;
    private BigInteger parallels = BigInteger.ZERO;
    private BigInteger energy = BigInteger.ZERO;

    public BigPrepaidParallelHelper(MTEMultiBlockBase controller, UUID owner, ParallelLimit limit,
        BigInteger unitEnergy, ParallelHelper nativeHelper) {
        this.controller = controller;
        this.owner = owner;
        this.limit = limit;
        this.unitEnergy = unitEnergy;
        NativeParallelPolicy.copyModifiers(nativeHelper, this);
    }

    @Override
    protected void determineParallel() {
        recipe = RecipeChanceEffects.apply(controller, recipe, chanceMultiplier);
        if (owner == null || unitEnergy.signum() < 0) {
            result = CheckRecipeResultRegistry.NO_RECIPE;
            return;
        }
        BigInteger available = GTWirelessEnergyNetwork.INSTANCE.getAvailableEUBig(owner);
        if (available.compareTo(unitEnergy) < 0) {
            result = CheckRecipeResultRegistry.insufficientStartupPower(unitEnergy);
            return;
        }
        inputs = new BigRecipeInputs(controller, recipe, itemInputs, fluidInputs);
        ParallelLimit cap = unitEnergy.signum() == 0 ? limit
            : ParallelLimit.bounded(limit.applyTo(available.divide(unitEnergy)));
        parallels = inputs.allocation()
            .maximum(cap);
        if (parallels.signum() == 0) {
            result = CheckRecipeResultRegistry.NO_RECIPE;
            return;
        }
        if (!capacity(parallels).wasSuccessful()) {
            BigInteger lower = BigInteger.ZERO, upper = parallels;
            while (lower.compareTo(upper) < 0) {
                BigInteger middle = lower.add(upper)
                    .add(BigInteger.ONE)
                    .shiftRight(1);
                if (capacity(middle).wasSuccessful()) lower = middle;
                else upper = middle.subtract(BigInteger.ONE);
            }
            parallels = lower;
            if (parallels.signum() == 0) {
                result = capacity(BigInteger.ONE);
                return;
            }
        }
        energy = unitEnergy.multiply(parallels);
        currentParallel = 1;
        itemOutputs = new ItemStack[0];
        fluidOutputs = new FluidStack[0];
        result = CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private List<IAEStack<?>> outputs(BigInteger count, boolean capacity) {
        return BigRecipeOutputs.calculate(controller, recipe, count, chanceMultiplier, capacity);
    }

    private CheckRecipeResult capacity(BigInteger count) {
        return BigRecipeOutputCapacity.check(controller, outputs(count, true));
    }

    /** The account is debited atomically before consuming inputs; a failed debit leaves every input intact. */
    public CheckRecipeResult start(int duration) {
        WirelessRecipeState state = ((BigWirelessController) controller).getWirelessRecipeState();
        if (state.isRunning() || parallels.signum() <= 0) return CheckRecipeResultRegistry.NO_RECIPE;
        if (!GTWirelessEnergyNetwork.INSTANCE.consumeEUBig(owner, energy))
            return CheckRecipeResultRegistry.insufficientStartupPower(energy);
        BigMachineOutputQueue products = new BigMachineOutputQueue();
        for (IAEStack<?> stack : outputs(parallels, false)) {
            if (stack instanceof IAEItemStack)
                products.addItem(((IAEItemStack) stack).getItemStack(), BigAEStackValues.get(stack));
            else products.addFluid(((IAEFluidStack) stack).getFluidStack(), BigAEStackValues.get(stack));
        }
        inputs.consume(parallels);
        state.startNativePowered(parallels, energy, duration, products);
        controller.markDirty();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    public BigInteger getParallelsBig() {
        return parallels;
    }

    public BigInteger getTotalEnergyBig() {
        return energy;
    }
}
