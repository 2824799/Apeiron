package com.silvia.apeiron.common.machine.spaceelevator;

import java.math.BigInteger;

import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;

import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Common state transition for custom ultimate elevator-module recipes. */
public final class SpaceElevatorRecipeSupport {

    private SpaceElevatorRecipeSupport() {}

    public static void start(MTEMultiBlockBase module, MTEInfiniteEnergyHatch hatch, BigInteger parallels,
        BigInteger totalEnergy, int duration, BigMachineOutputQueue outputs) {
        ((BigWirelessController) module).getWirelessRecipeState()
            .startExact(parallels, totalEnergy, duration, outputs, true);
        module.mOutputItems = new net.minecraft.item.ItemStack[0];
        module.mOutputFluids = new net.minecraftforge.fluids.FluidStack[0];
        module.mEUt = 0;
        if (module instanceof MTEExtendedPowerMultiBlockBase) ((MTEExtendedPowerMultiBlockBase) module).lEUt = 0;
        module.mEfficiencyIncrease = 10000;
        module.mMaxProgresstime = duration;
        module.markDirty();
    }
}
