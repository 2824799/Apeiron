package com.silvia.apeiron.common.machine.energy;

import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

public final class InfiniteEnergyHatches {

    private InfiniteEnergyHatches() {}

    public static MTEInfiniteEnergyHatch find(MTEMultiBlockBase machine) {
        for (MTEHatchEnergy hatch : machine.mEnergyHatches)
            if (hatch instanceof MTEInfiniteEnergyHatch && hatch.isValid()) return (MTEInfiniteEnergyHatch) hatch;
        return null;
    }

    public static boolean isNativeWirelessController(MTEMultiBlockBase machine) {
        // These bases charge whole recipes inside independent loops. They require their own cost adapters.
        for (Class<?> type = machine.getClass(); type != null; type = type.getSuperclass()) if (type.getSimpleName()
            .equals("WirelessEnergyMultiMachineBase")) return true;
        return false;
    }
}
