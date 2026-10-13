package com.silvia.apeiron.common.integration.messtech;

import java.math.BigInteger;

import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.api.machine.parallel.ParallelLimit;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.parallel.ExactProcessingContext;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;

import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;

/** Preserve native cross-recipe loops while reserving one exact quantity, energy and output budget. */
public final class MessRecipeBatch {

    private boolean active;
    private BigInteger parallels = BigInteger.ZERO;
    private BigInteger energy = BigInteger.ZERO;
    private BigMachineOutputQueue products = new BigMachineOutputQueue();

    public void begin() {
        active = true;
        parallels = BigInteger.ZERO;
        energy = BigInteger.ZERO;
        products = new BigMachineOutputQueue();
    }

    public CheckRecipeResult step(MTEMultiBlockBase machine) {
        if (!active || InfiniteEnergyHatches.find(machine) == null)
            return (CheckRecipeResult) MessTechAccess.call(machine, "wirelessModeProcessOnce");
        WirelessRecipeState settings = ((BigWirelessController) machine).getWirelessRecipeState();
        ParallelLimit limit = settings.getLimit();
        MessTechAccess.set(
            machine,
            "ownerUUID",
            InfiniteEnergyHatches.find(machine)
                .getOwnerUuid());
        try (ExactProcessingContext context = new ExactProcessingContext(
            machine,
            limit,
            energy,
            products.snapshotOutputsUnsorted())) {
            CheckRecipeResult result = (CheckRecipeResult) MessTechAccess.call(machine, "wirelessModeProcessOnce");
            if (result.wasSuccessful() && context.state.hasPreparedRecipe()) {
                context.state.commitPreparedRecipe();
                parallels = parallels.add(context.state.getParallelsBig());
                energy = energy.add(context.state.getTotalEUBig());
                context.state.complete();
                context.state.pending()
                    .moveTo(products);
            }
            return result;
        }
    }

    public void finish(MTEMultiBlockBase machine) {
        active = false;
        if (parallels.signum() == 0) return;
        WirelessRecipeState state = ((BigWirelessController) machine).getWirelessRecipeState();
        int ticks = InfiniteEnergyHatches.isUltimate(machine) ? InfiniteEnergyHatches.targetDuration(machine)
            : Math.max(1, machine.mMaxProgresstime);
        state.startExact(parallels, energy, ticks, products, InfiniteEnergyHatches.isUltimate(machine));
        products = new BigMachineOutputQueue();
        machine.mMaxProgresstime = ticks;
        ((MTEExtendedPowerMultiBlockBase) machine).lEUt = 0;
        MessTechAccess.set(machine, "costingEU", energy);
        MessTechAccess.set(machine, "costingEUText", energy.toString());
    }
}
