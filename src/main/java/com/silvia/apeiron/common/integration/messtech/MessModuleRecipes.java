package com.silvia.apeiron.common.integration.messtech;

import java.math.BigInteger;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;

import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.api.machine.parallel.ParallelLimit;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.output.BigRecipeOutputCapacity;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;

import gregtech.api.enums.GTValues;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gtnhintergalactic.recipe.SpacePumpingRecipes;

/** Exact generated products keep native selections, bee simulation and module cost formulas. */
public final class MessModuleRecipes {

    private MessModuleRecipes() {}

    public static CheckRecipeResult pump(MTEMultiBlockBase machine) {
        MTEInfiniteEnergyHatch hatch = InfiniteEnergyHatches.find(machine);
        if (hatch == null) return null;
        WirelessRecipeState settings = ((BigWirelessController) machine).getWirelessRecipeState();
        if (settings.isRunning()) return CheckRecipeResultRegistry.NO_RECIPE;
        int[] planets = (int[]) MessTechAccess.get(machine, "planetTypes");
        int[] gases = (int[]) MessTechAccess.get(machine, "gasTypes");
        int batch = Math.max(1, Math.min(128, ((Number) MessTechAccess.get(machine, "batchSize")).intValue()));
        int baseTicks = 20 * batch;
        BigInteger budget = hatch.getAvailableEUBig();
        BigInteger count = BigInteger.ZERO, cost = BigInteger.ZERO;
        BigMachineOutputQueue products = new BigMachineOutputQueue();
        for (int i = 0; i < Math.min(planets.length, gases.length); i++) {
            FluidStack fluid = SpacePumpingRecipes.RECIPES.get(Pair.of(planets[i], gases[i]));
            if (fluid == null) continue;
            ParallelLimit limit = settings.getLimit();
            BigInteger overhead = BigInteger.valueOf(GTValues.V[14] - GTValues.V[13])
                .multiply(BigInteger.valueOf(baseTicks));
            BigInteger per = BigInteger.valueOf(GTValues.V[13])
                .multiply(BigInteger.valueOf(baseTicks));
            BigInteger cap = budget.subtract(cost)
                .subtract(overhead)
                .max(BigInteger.ZERO)
                .divide(per);
            cap = limit.applyTo(cap);
            if (!hatch.isUltimate() && InfiniteEnergyHatches.processingVoltage(machine) < GTValues.V[14])
                return CheckRecipeResultRegistry.insufficientVoltage(GTValues.V[14]);
            final BigMachineOutputQueue reserved = products;
            final BigInteger outputPer = BigInteger.valueOf((long) fluid.amount * batch);
            cap = fit(machine, cap, value -> {
                BigMachineOutputQueue candidate = copy(reserved);
                candidate.addFluid(fluid, outputPer.multiply(value));
                return candidate;
            });
            if (cap.signum() == 0) continue;
            products.addFluid(fluid, outputPer.multiply(cap));
            cost = cost.add(overhead)
                .add(per.multiply(cap));
            count = count.add(cap);
        }
        if (count.signum() == 0) return CheckRecipeResultRegistry.NO_RECIPE;
        start(
            machine,
            count,
            cost,
            hatch.isUltimate() ? InfiniteEnergyHatches.targetDuration(machine) : baseTicks + 3,
            products);
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    public static CheckRecipeResult apiary(MTEMultiBlockBase machine) {
        MTEInfiniteEnergyHatch hatch = InfiniteEnergyHatches.find(machine);
        if (hatch == null) return null;
        WirelessRecipeState settings = ((BigWirelessController) machine).getWirelessRecipeState();
        if (settings.isRunning()) return CheckRecipeResultRegistry.NO_RECIPE;
        if (!(Boolean) MessTechAccess.call(machine, "hasAnyBee")) return CheckRecipeResultRegistry.NO_RECIPE;
        int ticks = ((Number) MessTechAccess.call(machine, "getMainBatchDuration")).intValue();
        int tier = ((Number) MessTechAccess.call(machine, "getTier")).intValue();
        BigInteger unitEU = BigInteger.valueOf(GTValues.V[Math.max(0, Math.min(GTValues.V.length - 1, tier))]);
        if (!hatch.isUltimate()
            && unitEU.compareTo(BigInteger.valueOf(InfiniteEnergyHatches.processingVoltage(machine))) > 0)
            return CheckRecipeResultRegistry.insufficientVoltage(unitEU.longValue());
        BigInteger per = unitEU.multiply(BigInteger.valueOf(ticks));
        BigInteger cap = settings.getLimit()
            .applyTo(
                hatch.getAvailableEUBig()
                    .divide(per));
        if (cap.signum() == 0) return CheckRecipeResultRegistry.NO_RECIPE;
        ItemStack[] drops = (ItemStack[]) MessTechAccess.call(machine, "runBees", 1);
        BigMachineOutputQueue base = new BigMachineOutputQueue();
        for (ItemStack drop : drops) if (drop != null) base.addItem(drop, BigInteger.valueOf(drop.stackSize));
        cap = fit(machine, cap, value -> {
            BigMachineOutputQueue candidate = copy(base);
            candidate.multiply(value);
            return candidate;
        });
        if (cap.signum() == 0) return CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;
        BigMachineOutputQueue products = copy(base);
        products.multiply(cap);
        start(
            machine,
            cap,
            per.multiply(cap),
            hatch.isUltimate() ? InfiniteEnergyHatches.targetDuration(machine) : ticks,
            products);
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    static BigInteger fit(MTEMultiBlockBase machine, BigInteger cap,
        java.util.function.Function<BigInteger, BigMachineOutputQueue> products) {
        BigInteger low = BigInteger.ZERO, high = cap;
        if (cap.signum() <= 0) return BigInteger.ZERO;
        if (BigRecipeOutputCapacity.check(
            machine,
            products.apply(cap)
                .snapshotOutputsUnsorted())
            .wasSuccessful()) return cap;
        while (low.compareTo(high) < 0) {
            BigInteger middle = low.add(high)
                .add(BigInteger.ONE)
                .shiftRight(1);
            if (BigRecipeOutputCapacity.check(
                machine,
                products.apply(middle)
                    .snapshotOutputsUnsorted())
                .wasSuccessful()) low = middle;
            else high = middle.subtract(BigInteger.ONE);
        }
        return low;
    }

    static BigMachineOutputQueue copy(BigMachineOutputQueue source) {
        BigMachineOutputQueue result = new BigMachineOutputQueue();
        for (appeng.api.storage.data.IAEStack<?> value : source.snapshotOutputsUnsorted()) {
            BigInteger count = com.silvia.apeiron.ae.stack.BigAEStackValues.get(value);
            if (value instanceof appeng.api.storage.data.IAEItemStack)
                result.addItem(((appeng.api.storage.data.IAEItemStack) value).getItemStack(), count);
            else result.addFluid(((appeng.api.storage.data.IAEFluidStack) value).getFluidStack(), count);
        }
        return result;
    }

    static void start(MTEMultiBlockBase machine, BigInteger parallels, BigInteger cost, int ticks,
        BigMachineOutputQueue products) {
        ((BigWirelessController) machine).getWirelessRecipeState()
            .startExact(parallels, cost, ticks, products, InfiniteEnergyHatches.isUltimate(machine));
        machine.mMaxProgresstime = ticks;
        machine.mOutputItems = new ItemStack[0];
        machine.mOutputFluids = new FluidStack[0];
        ((MTEExtendedPowerMultiBlockBase) machine).lEUt = 0;
        machine.mEfficiency = machine.mEfficiencyIncrease = 10000;
        MessTechAccess.set(machine, "costingEU", cost);
        MessTechAccess.set(machine, "costingEUText", cost.toString());
        machine.markDirty();
    }
}
