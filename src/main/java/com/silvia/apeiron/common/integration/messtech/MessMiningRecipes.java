package com.silvia.apeiron.common.integration.messtech;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigSpaceElevatorModule;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.api.machine.parallel.ParallelLimit;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.parallel.BigRecipeInputs;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.math.MiningOutputCounts;

import appeng.util.item.AEFluidStack;
import gregtech.api.enums.GTValues;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.objects.XSTR;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gtnhintergalactic.recipe.IGRecipeMaps;
import gtnhintergalactic.recipe.SpaceMiningData;
import gtnhintergalactic.recipe.SpaceMiningRecipes.WeightedAsteroidList;
import gtnhintergalactic.spaceprojects.ProjectAsteroidOutpost;

/** Native asteroid selection and resource conditions around an exact, bounded-cost mining batch. */
public final class MessMiningRecipes {

    private MessMiningRecipes() {}

    public static CheckRecipeResult process(MTEMultiBlockBase machine) {
        MTEInfiniteEnergyHatch hatch = InfiniteEnergyHatches.find(machine);
        if (hatch == null) return null;
        MTEMultiBlockBase parent = ((BigSpaceElevatorModule) machine).getEnergyParent();
        java.util.List<gregtech.common.tileentities.machines.IRecipeProcessingAwareHatch> owned = new java.util.ArrayList<>();
        if (parent != null) for (Object input : parent.mInputHatches) {
            boolean open = input instanceof com.silvia.apeiron.common.machine.me.stocking.StockingInputHost
                ? !((com.silvia.apeiron.common.machine.me.stocking.StockingInputHost) input).getStockingInput()
                    .isProcessing()
                : input instanceof gregtech.common.tileentities.machines.MTEHatchInputME
                    && !(Boolean) MessTechAccess.get(input, "processingRecipe");
            if (open && input instanceof gregtech.common.tileentities.machines.IRecipeProcessingAwareHatch) {
                gregtech.common.tileentities.machines.IRecipeProcessingAwareHatch aware = (gregtech.common.tileentities.machines.IRecipeProcessingAwareHatch) input;
                aware.startRecipeProcessing();
                owned.add(aware);
            }
        }
        try {
            return processExact(machine, hatch, parent);
        } finally {
            for (gregtech.common.tileentities.machines.IRecipeProcessingAwareHatch input : owned)
                machine.setResultIfFailure(input.endRecipeProcessing(parent));
        }
    }

    private static CheckRecipeResult processExact(MTEMultiBlockBase machine, MTEInfiniteEnergyHatch hatch,
        MTEMultiBlockBase parent) {
        WirelessRecipeState settings = ((BigWirelessController) machine).getWirelessRecipeState();
        if (settings.isRunning()) return CheckRecipeResultRegistry.NO_RECIPE;
        BigMachineOutputQueue products = new BigMachineOutputQueue();
        BigInteger count = BigInteger.ZERO, cost = BigInteger.ZERO, computation = BigInteger.ZERO;
        int cycles = ((Number) MessTechAccess.call(machine, "getBatchTaskCount")).intValue();
        int successful = 0;
        for (int cycle = 0; cycle < cycles; cycle++) {
            ItemStack[] items = (ItemStack[]) MessTechAccess.call(machine, "gatherItemInputs");
            ArrayList<FluidStack> fluidList = new ArrayList<>();
            if (parent != null) fluidList.addAll(parent.getStoredFluids());
            fluidList.addAll((ArrayList<FluidStack>) MessTechAccess.call(machine, "getStoredFluidsWithDualInput"));
            FluidStack[] fluids = fluidList.toArray(new FluidStack[0]);
            if (items.length == 0 || fluids.length == 0) break;
            MessTechAccess
                .set(machine, "currentDroneMask", MessTechAccess.call(machine, "getAvailDroneMask", (Object) items));
            int tier = ((Number) MessTechAccess.call(machine, "getBestPlasmaTier", (Object) fluids)).intValue();
            if (tier <= 0) break;
            FluidStack plasma = (FluidStack) MessTechAccess.call(machine, "findPlasma", fluids, tier);
            int distance = ((Number) MessTechAccess.call(machine, "getEffectiveDistance")).intValue();
            WeightedAsteroidList candidates = new WeightedAsteroidList(
                IGRecipeMaps.spaceMiningRecipes.findRecipeQuery()
                    .items(items)
                    .fluids(fluids)
                    .voltage(Long.MAX_VALUE)
                    .findAll()
                    .filter(recipe -> {
                        SpaceMiningData data = recipe.getMetadata(IGRecipeMaps.SPACE_MINING_DATA);
                        return data != null && data.minDistance <= distance && data.maxDistance >= distance;
                    })
                    .distinct());
            if (candidates.totalWeight == 0) break;
            GTRecipe recipe = candidates.getRandom();
            SpaceMiningData data = recipe.getMetadata(IGRecipeMaps.SPACE_MINING_DATA);
            if (!com.silvia.apeiron.common.machine.parallel.BigRecipeParallelHelper.supportsInputs(recipe)) break;
            if (data.minSize < 0 || data.maxSize < data.minSize || plasma == null) break;
            int ticks = Math
                .max(1, ((Number) MessTechAccess.call(machine, "getRecipeTime", recipe.mDuration, tier)).intValue());
            ProjectAsteroidOutpost outpost = (ProjectAsteroidOutpost) MessTechAccess.get(machine, "asteroidOutpost");
            BigDecimal compPer = decimal(
                data.computation,
                outpost == null ? 1f : 1f - outpost.getComputationDiscount());
            BigDecimal plasmaPer = decimal(
                ((Number) MessTechAccess.call(machine, "getPlasmaUsageFromTier", tier)).intValue(),
                outpost == null ? 1f : 1f - outpost.getPlasmaDiscount());
            BigInteger availableData = BigInteger
                .valueOf(Math.max(0, ((Number) MessTechAccess.call(machine, "getAvailableDataForModule")).longValue()))
                .subtract(computation)
                .max(BigInteger.ZERO);
            BigInteger overhead = BigInteger.valueOf(GTValues.V[14] - GTValues.V[13])
                .multiply(BigInteger.valueOf(ticks));
            BigInteger perEnergy = BigInteger.valueOf(GTValues.V[13])
                .multiply(BigInteger.valueOf(ticks));
            BigInteger cap = settings.getLimit()
                .applyTo(
                    hatch.getAvailableEUBig()
                        .subtract(cost)
                        .subtract(overhead)
                        .max(BigInteger.ZERO)
                        .divide(perEnergy));
            if (compPer.signum() > 0) cap = cap.min(
                new BigDecimal(availableData).divide(compPer, 0, RoundingMode.FLOOR)
                    .toBigIntegerExact());
            if (!hatch.isUltimate() && InfiniteEnergyHatches.processingVoltage(machine) < GTValues.V[14]) break;
            if (cap.signum() == 0) break;
            BigInteger low = BigInteger.ZERO, high = cap;
            while (low.compareTo(high) < 0) {
                BigInteger middle = low.add(high)
                    .add(BigInteger.ONE)
                    .shiftRight(1);
                BigRecipeInputs inputs = inputs(machine, parent, recipe, items, fluids, plasma, plasmaPer, middle);
                boolean fits = inputs != null && inputs.allocation()
                    .maximum(ParallelLimit.bounded(middle))
                    .equals(middle);
                if (fits) low = middle;
                else high = middle.subtract(BigInteger.ONE);
            }
            cap = MessModuleRecipes.fit(machine, low, parallels -> {
                BigMachineOutputQueue candidate = MessModuleRecipes.copy(products);
                BigInteger rolls = parallels.multiply(BigInteger.valueOf(data.maxSize));
                for (ItemStack output : recipe.mOutputs) if (accept(machine, output))
                    candidate.addItem(output, rolls.multiply(BigInteger.valueOf(output.stackSize)));
                return candidate;
            });
            if (cap.signum() == 0) break;
            BigRecipeInputs inputs = inputs(machine, parent, recipe, items, fluids, plasma, plasmaPer, cap);
            BigMachineOutputQueue generated = outputs(machine, recipe, data, cap, tier);
            inputs.consume(cap);
            generated.moveTo(products);
            computation = computation.add(ceil(compPer.multiply(new BigDecimal(cap))));
            cost = cost.add(overhead)
                .add(perEnergy.multiply(cap));
            count = count.add(cap);
            successful++;
            MessTechAccess.call(machine, "cycleDistance");
        }
        if (count.signum() == 0) return CheckRecipeResultRegistry.NO_RECIPE;
        MessTechAccess.set(machine, "eRequiredData", computation.longValueExact());
        MessTechAccess.set(
            machine,
            "eAvailableData",
            Math.max(
                computation.longValueExact(),
                ((Number) MessTechAccess.get(machine, "eAvailableData")).longValue()));
        MessTechAccess.set(machine, "eComputationTimeout", 100);
        MessModuleRecipes.start(
            machine,
            count,
            cost,
            hatch.isUltimate() ? InfiniteEnergyHatches.targetDuration(machine) : 20 + Math.max(0, successful - 1),
            products);
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private static BigRecipeInputs inputs(MTEMultiBlockBase machine, MTEMultiBlockBase parent, GTRecipe recipe,
        ItemStack[] items, FluidStack[] fluids, FluidStack plasma, BigDecimal plasmaPer, BigInteger count) {
        try {
            return new BigRecipeInputs(
                machine,
                parent,
                recipe,
                items,
                fluids,
                Collections.singletonList(
                    BigAEStackValues
                        .copyWithSize(AEFluidStack.create(plasma), ceil(plasmaPer.multiply(new BigDecimal(count))))));
        } catch (IllegalArgumentException missing) {
            return null;
        }
    }

    static BigMachineOutputQueue outputs(MTEMultiBlockBase machine, GTRecipe recipe, SpaceMiningData data,
        BigInteger parallels, int tier) {
        BigInteger rolls = parallels.multiply(BigInteger.valueOf(data.minSize))
            .add(
                MiningOutputCounts.binomial(
                    parallels.multiply(BigInteger.valueOf(data.maxSize - data.minSize)),
                    Math.max(
                        0,
                        Math.min(
                            10000,
                            ((Number) MessTechAccess.call(machine, "getBonusStackChance", tier)).intValue())),
                    10000,
                    XSTR.XSTR_INSTANCE));
        int remaining = 0;
        for (int i = 0; i < recipe.mOutputs.length; i++)
            remaining = Math.addExact(remaining, recipe.getOutputChance(i));
        BigMachineOutputQueue result = new BigMachineOutputQueue();
        for (int i = 0; i < recipe.mOutputs.length; i++) {
            int chance = recipe.getOutputChance(i);
            if (chance <= 0) continue;
            BigInteger amount = MiningOutputCounts.binomial(rolls, chance, remaining, XSTR.XSTR_INSTANCE);
            remaining -= chance;
            rolls = rolls.subtract(amount);
            if (accept(machine, recipe.mOutputs[i]))
                result.addItem(recipe.mOutputs[i], amount.multiply(BigInteger.valueOf(recipe.mOutputs[i].stackSize)));
        }
        return result;
    }

    private static boolean accept(MTEMultiBlockBase machine, ItemStack item) {
        Set<?> filter = (Set<?>) MessTechAccess.get(machine, "configuredOres");
        return item != null && (filter == null || filter.isEmpty()
            || (Boolean) MessTechAccess.get(machine, "isWhitelisted")
                == filter.contains(MessTechAccess.call(machine, "getOreString", item)));
    }

    private static BigDecimal decimal(int amount, float multiplier) {
        return new BigDecimal(Float.toString(Math.max(0, multiplier))).multiply(BigDecimal.valueOf(amount));
    }

    private static BigInteger ceil(BigDecimal value) {
        return value.setScale(0, RoundingMode.CEILING)
            .toBigIntegerExact();
    }
}
