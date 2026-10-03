package com.silvia.apeiron.common.machine.energy;

import java.math.BigInteger;

import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Shared atomic debit used by both GT running bases; a failed debit does not advance progress. */
public final class WirelessControllerEnergy {

    private WirelessControllerEnergy() {}

    public static void complete(MTEMultiBlockBase machine) {
        WirelessRecipeState state = ((BigWirelessController) machine).getWirelessRecipeState();
        state.complete();
        if (!state.pending()
            .isEmpty() && state.pending()
                .flush(
                    machine.getOutputBusses(),
                    machine.getOutputHatches(),
                    machine.protectsExcessItem(),
                    machine.protectsExcessFluid()))
            machine.markDirty();
    }

    public static boolean debitTick(MTEMultiBlockBase machine) {
        WirelessRecipeState state = ((BigWirelessController) machine).getWirelessRecipeState();
        MTEInfiniteEnergyHatch hatch = InfiniteEnergyHatches.find(machine);
        BigInteger debit = state.nextDebit(machine.mEfficiency);
        if (hatch == null || !hatch.consumeEUBig(debit)) return false;
        state.paidTick();
        return true;
    }

    public static Boolean debitLegacy(MTEMultiBlockBase machine, long amount) {
        MTEInfiniteEnergyHatch hatch = InfiniteEnergyHatches.find(machine);
        if (hatch == null || InfiniteEnergyHatches.isNativeWirelessController(machine)) return null;
        return amount >= 0 && hatch.consumeEUBig(BigInteger.valueOf(amount));
    }
}
