package com.silvia.apeiron.common.machine.energy;

import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;

import com.silvia.apeiron.api.machine.parallel.BigPurificationPlant;
import com.silvia.apeiron.api.machine.parallel.BigPurificationUnit;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.parallel.PurificationParallels;
import com.silvia.apeiron.common.machine.parallel.PurificationWaterInputs;
import com.silvia.apeiron.math.BigNumberFormatter;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitBase;

public final class PurificationPlantEnergy {

    private PurificationPlantEnergy() {}

    public static Boolean debitTick(MTEMultiBlockBase plant) {
        if (!(plant instanceof BigPurificationPlant) || PurificationWaterInputs.usesSplitCycleOutputs(plant))
            return null;
        MTEInfiniteEnergyHatch hatch = InfiniteEnergyHatches.find(plant);
        if (hatch == null) return null;
        BigInteger power = ((BigPurificationPlant) plant).getPurificationPowerUsageBig();
        BigInteger debit = power.multiply(BigInteger.valueOf(10000))
            .divide(BigInteger.valueOf(Math.max(1000, plant.mEfficiency)));
        if (hatch.consumeEUBig(debit)) return true;
        plant.stopMachine(ShutDownReasonRegistry.POWER_LOSS);
        return false;
    }

    public static boolean writeDisplay(MTEMultiBlockBase machine, NBTTagCompound tag) {
        if (PurificationWaterInputs.usesSplitCycleOutputs(machine)) return false;
        int efficiency;
        if (machine instanceof BigPurificationUnit) {
            MTEPurificationUnitBase<?> unit = (MTEPurificationUnitBase<?>) machine;
            if (PurificationParallels.source(unit) == null) return false;
            efficiency = unit.getController().mEfficiency;
            BigInteger setting = ((BigPurificationUnit) unit).getPurificationParallelSettingBig();
            tag.setString(
                "ApeironParallelSetting",
                setting.signum() == 0 ? "∞" : BigNumberFormatter.formatCompact(setting));
        } else if (machine instanceof BigPurificationPlant && InfiniteEnergyHatches.find(machine) != null) {
            efficiency = machine.mEfficiency;
            // The plant's native cycle does not use its own generic recipe parallel setting.
            tag.removeTag("ApeironParallelSetting");
        } else return false;
        BigWirelessController controller = (BigWirelessController) machine;
        BigInteger power = controller.getRecipeEUtBig()
            .multiply(BigInteger.valueOf(10000))
            .divide(BigInteger.valueOf(Math.max(1000, efficiency)));
        boolean running = machine.mMaxProgresstime > 0;
        tag.setBoolean("ApeironWirelessRunning", running);
        tag.setString("ApeironRunningParallels", BigNumberFormatter.formatCompact(controller.getCurrentParallelsBig()));
        tag.setString("ApeironWirelessEUt", BigNumberFormatter.formatCompact(power));
        tag.removeTag("ApeironWirelessOutputRows");
        tag.removeTag("ApeironWirelessOutputTypes");
        if (running) {
            BigInteger total = power.multiply(BigInteger.valueOf(machine.mMaxProgresstime));
            tag.setBoolean("ApeironNativeRecipeEnergy", true);
            tag.setString("ApeironActualWirelessEUt", power.toString());
            tag.setString("ApeironActualWirelessEUtDisplay", BigNumberFormatter.formatCompact(power));
            tag.setString("ApeironActualWirelessTotalEU", total.toString());
            tag.setString("ApeironActualWirelessTotalEUDisplay", BigNumberFormatter.formatCompact(total));
        } else {
            tag.removeTag("ApeironNativeRecipeEnergy");
            tag.removeTag("ApeironActualWirelessEUt");
            tag.removeTag("ApeironActualWirelessEUtDisplay");
            tag.removeTag("ApeironActualWirelessTotalEU");
            tag.removeTag("ApeironActualWirelessTotalEUDisplay");
        }
        return true;
    }
}
