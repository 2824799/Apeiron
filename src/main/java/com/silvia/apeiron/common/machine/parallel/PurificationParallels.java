package com.silvia.apeiron.common.machine.parallel;

import java.math.BigInteger;

import com.silvia.apeiron.api.machine.parallel.BigPurificationPlant;
import com.silvia.apeiron.api.machine.parallel.BigPurificationUnit;
import com.silvia.apeiron.api.machine.parallel.ParallelLimit;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;

import gregtech.common.tileentities.machines.multi.purification.MTEPurificationPlant;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitBase;

/** Native unit settings stay independent; their linked plant supplies the exact energy budget. */
public final class PurificationParallels {

    private PurificationParallels() {}

    public static MTEInfiniteEnergyHatch source(MTEPurificationUnitBase<?> unit) {
        MTEPurificationPlant plant = unit.getController();
        return plant == null || PurificationWaterInputs.usesSplitCycleOutputs(plant) ? null
            : InfiniteEnergyHatches.find(plant);
    }

    public static ParallelLimit limit(MTEPurificationUnitBase<?> unit) {
        if (source(unit) == null) return ParallelLimit.bounded(Math.max(1, unit.getMaxParallel()));
        BigInteger setting = ((BigPurificationUnit) unit).getPurificationParallelSettingBig();
        return setting.signum() == 0 ? ParallelLimit.unlimited() : ParallelLimit.bounded(setting);
    }

    /** Reserve the remaining cycle budget of earlier units without charging it twice. */
    public static BigInteger affordable(MTEPurificationUnitBase<?> unit) {
        MTEInfiniteEnergyHatch hatch = source(unit);
        if (hatch == null) return BigInteger.valueOf(Math.max(1, unit.getMaxParallel()));
        MTEPurificationPlant plant = unit.getController();
        int ticks = Math.max(1, plant.mMaxProgresstime - plant.mProgresstime);
        BigInteger power = ((BigPurificationPlant) plant).getPurificationPowerUsageBig();
        if (unit.mMaxProgresstime > 0)
            power = power.subtract(((BigPurificationUnit) unit).getPurificationPowerUsageBig());
        BigInteger available = hatch.getAvailableEUBig()
            .divide(BigInteger.valueOf(ticks))
            .multiply(BigInteger.valueOf(Math.max(1000, plant.mEfficiency)))
            .divide(BigInteger.valueOf(10000))
            .subtract(power)
            .max(BigInteger.ZERO);
        return available.divide(BigInteger.valueOf(Math.max(1, unit.getBasePowerUsage())));
    }
}
