package com.silvia.apeiron.common.machine.spaceelevator;

import java.math.BigInteger;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;

import com.silvia.apeiron.api.machine.parallel.BigSpaceElevatorModule;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.api.machine.parallel.ParallelLimit;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.energy.WirelessWailaDisplay;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.config.MixinFeature;
import com.silvia.apeiron.math.BigNumberFormatter;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gtnhintergalactic.tile.multi.elevator.TileEntitySpaceElevator;
import gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleBase;

/** The module parameter is the cap; the parent's generic power panel does not limit module recipes. */
public final class SpaceElevatorWailaSnapshot {

    private SpaceElevatorWailaSnapshot() {}

    public static boolean write(MTEMultiBlockBase machine, NBTTagCompound tag) {
        if (!MixinFeature.SPACE_ELEVATOR.isEnabled()
            || !(machine instanceof BigSpaceElevatorModule) && !(machine instanceof TileEntitySpaceElevator)
            || InfiniteEnergyHatches.find(machine) == null) return false;
        if (machine instanceof BigSpaceElevatorModule) {
            BigWirelessController controller = (BigWirelessController) machine;
            WirelessRecipeState state = controller.getWirelessRecipeState();
            WirelessWailaDisplay.write(tag, state, machine.mEfficiency);
            writeLimit(tag, ((BigSpaceElevatorModule) machine).getModuleParallelLimitBig());
            if (!state.isRunning() && machine.mMaxProgresstime > 0) {
                BigInteger power = nativePower((TileEntityModuleBase) machine);
                writePower(
                    tag,
                    true,
                    controller.getCurrentParallelsBig(),
                    power,
                    power.multiply(BigInteger.valueOf(machine.mMaxProgresstime)));
            }
            return true;
        }
        if (!(machine instanceof TileEntitySpaceElevator)) return false;
        TileEntitySpaceElevator elevator = (TileEntitySpaceElevator) machine;
        BigInteger limit = BigInteger.ZERO;
        BigInteger parallels = BigInteger.ZERO;
        BigInteger power = BigInteger.ZERO;
        BigInteger totalEnergy = BigInteger.ZERO;
        boolean unlimited = false;
        boolean running = false;
        Set<TileEntityModuleBase> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        if (elevator.mProjectModuleHatches != null) for (TileEntityModuleBase module : elevator.mProjectModuleHatches) {
            if (!(module instanceof BigSpaceElevatorModule) || !seen.add(module)
                || ((BigSpaceElevatorModule) module).getEnergyParent() != elevator) continue;
            ParallelLimit moduleLimit = ((BigSpaceElevatorModule) module).getModuleParallelLimitBig();
            unlimited |= moduleLimit.isUnlimited();
            limit = limit.add(
                moduleLimit.getBound()
                    .orElse(BigInteger.ZERO));
            BigWirelessController controller = (BigWirelessController) module;
            WirelessRecipeState state = controller.getWirelessRecipeState();
            if (state.isRunning()) {
                running = true;
                parallels = parallels.add(controller.getCurrentParallelsBig());
                power = power.add(state.displayEUt(module.mEfficiency));
                totalEnergy = totalEnergy.add(state.displayTotalEU(module.mEfficiency));
            } else if (module.mMaxProgresstime > 0) {
                running = true;
                BigInteger nativePower = nativePower(module);
                power = power.add(nativePower);
                totalEnergy = totalEnergy.add(nativePower.multiply(BigInteger.valueOf(module.mMaxProgresstime)));
                parallels = parallels.add(controller.getCurrentParallelsBig());
            }
        }
        // Clear active output/voltage fields from any earlier request, then supply the aggregate snapshot.
        WirelessWailaDisplay.write(tag, new WirelessRecipeState(), machine.mEfficiency);
        writeLimit(tag, unlimited ? ParallelLimit.unlimited() : ParallelLimit.bounded(limit));
        writePower(tag, running, parallels, power, totalEnergy);
        return true;
    }

    private static BigInteger nativePower(TileEntityModuleBase module) {
        // The native getter returns lEUt, but older GT versions only expose the shared long field.
        return BigInteger.valueOf(module.lEUt)
            .negate()
            .max(BigInteger.ZERO)
            .multiply(BigInteger.valueOf(module.eAmpereFlow))
            .multiply(BigInteger.valueOf(10000))
            .divide(BigInteger.valueOf(Math.max(1000, module.mEfficiency)));
    }

    private static void writePower(NBTTagCompound tag, boolean running, BigInteger parallels, BigInteger power,
        BigInteger totalEnergy) {
        tag.setBoolean("ApeironWirelessRunning", running);
        tag.setString("ApeironRunningParallels", BigNumberFormatter.formatCompact(parallels));
        tag.setString("ApeironWirelessEUt", BigNumberFormatter.formatCompact(power));
        tag.setBoolean("ApeironNativeRecipeEnergy", true);
        tag.setString("ApeironActualWirelessEUt", power.toString());
        tag.setString("ApeironActualWirelessEUtDisplay", BigNumberFormatter.formatCompact(power));
        tag.setString("ApeironActualWirelessTotalEU", totalEnergy.toString());
        tag.setString("ApeironActualWirelessTotalEUDisplay", BigNumberFormatter.formatCompact(totalEnergy));
    }

    private static void writeLimit(NBTTagCompound tag, ParallelLimit limit) {
        tag.setString(
            "ApeironParallelSetting",
            limit.isUnlimited() ? "∞"
                : BigNumberFormatter.formatCompact(
                    limit.getBound()
                        .get()));
    }
}
