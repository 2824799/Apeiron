package com.silvia.apeiron.common.machine.energy;

import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

public final class InfiniteEnergyHatches {

    private InfiniteEnergyHatches() {}

    public static MTEInfiniteEnergyHatch find(MTEMultiBlockBase machine) {
        MTEInfiniteEnergyHatch ordinary = null;
        for (MTEHatchEnergy hatch : machine.mEnergyHatches)
            if (hatch instanceof MTEInfiniteEnergyHatch && hatch.isValid()) {
                MTEInfiniteEnergyHatch source = (MTEInfiniteEnergyHatch) hatch;
                if (source.isUltimate()) return source;
                ordinary = source;
            }
        return ordinary;
    }

    public static boolean isUltimate(MTEMultiBlockBase machine) {
        MTEInfiniteEnergyHatch hatch = find(machine);
        return hatch != null && hatch.isUltimate();
    }

    public static long processingVoltage(MTEMultiBlockBase machine) {
        return isUltimate(machine) ? Long.MAX_VALUE
            : ((com.silvia.apeiron.api.machine.parallel.BigWirelessController) machine).getWirelessRecipeState()
                .getVoltageSetting();
    }

    public static boolean isNativeWirelessController(MTEMultiBlockBase machine) {
        // These bases charge whole recipes inside independent loops. They require their own cost adapters.
        for (Class<?> type = machine.getClass(); type != null; type = type.getSuperclass()) if (type.getSimpleName()
            .equals("WirelessEnergyMultiMachineBase")) return true;
        return false;
    }
}
