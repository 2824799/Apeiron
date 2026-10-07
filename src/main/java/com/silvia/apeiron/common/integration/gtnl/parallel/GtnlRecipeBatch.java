package com.silvia.apeiron.common.integration.gtnl.parallel;

import java.math.BigInteger;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import com.science.gtnl.common.machine.multiMachineBase.WirelessEnergyMultiMachineBase;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.api.machine.parallel.ParallelLimit;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.math.BigNumberFormatter;

import gregtech.api.util.GTRecipe;

/** One native cross-recipe loop becomes one exact batch; nested subclass checks share the same ledger. */
public final class GtnlRecipeBatch {

    private int depth;
    private BigInteger parallels = BigInteger.ZERO;
    private BigInteger totalEnergy = BigInteger.ZERO;
    private long totalDuration;
    private BigMachineOutputQueue outputs = new BigMachineOutputQueue();
    private final Set<GTRecipe> catalysts = Collections.newSetFromMap(new IdentityHashMap<>());

    public boolean isActive() {
        return depth > 0;
    }

    public BigInteger getTotalEnergy() {
        return totalEnergy;
    }

    public BigMachineOutputQueue outputs() {
        return outputs;
    }

    public void enter(WirelessEnergyMultiMachineBase<?> machine) {
        if (depth++ != 0) return;
        parallels = BigInteger.ZERO;
        totalEnergy = BigInteger.ZERO;
        totalDuration = 0;
        outputs = new BigMachineOutputQueue();
        catalysts.clear();
    }

    public ParallelLimit limit(WirelessRecipeState settings, GTRecipe recipe, boolean consumes) {
        if (!consumes) return catalysts.contains(recipe) ? ParallelLimit.bounded(BigInteger.ZERO) : settings.getLimit();
        ParallelLimit requested = settings.getLimit();
        return requested.isUnlimited() ? requested
            : ParallelLimit.bounded(
                settings.getParallelSettingBig()
                    .subtract(parallels)
                    .max(BigInteger.ZERO));
    }

    public boolean canAppend(int ticks, boolean ultimate) {
        return ultimate || totalDuration + ticks < Integer.MAX_VALUE;
    }

    public void append(BigGtnlParallelHelper recipe, BigMachineOutputQueue products) {
        parallels = parallels.add(recipe.getParallelsBig());
        totalEnergy = totalEnergy.add(recipe.getTotalEnergyBig());
        totalDuration += recipe.getDuration();
        products.moveTo(outputs);
        if (!recipe.consumesResources()) catalysts.add(recipe.getOriginalRecipe());
    }

    public void leave(WirelessEnergyMultiMachineBase<?> machine) {
        if (depth <= 0 || --depth != 0 || parallels.signum() == 0) return;
        WirelessRecipeState state = ((BigWirelessController) machine).getWirelessRecipeState();
        int ticks = InfiniteEnergyHatches.isUltimate(machine) ? state.getTargetDuration()
            : Math.max(1, machine.mMaxProgresstime);
        machine.mMaxProgresstime = ticks;
        machine.lEUt = 0;
        machine.costingEU = totalEnergy;
        machine.costingEUText = BigNumberFormatter.formatCompact(totalEnergy);
        if (InfiniteEnergyHatches.find(machine) != null)
            state.startExact(parallels, totalEnergy, ticks, outputs, InfiniteEnergyHatches.isUltimate(machine));
        else state.startNativePowered(parallels, totalEnergy, ticks, outputs);
        outputs = new BigMachineOutputQueue();
    }
}
