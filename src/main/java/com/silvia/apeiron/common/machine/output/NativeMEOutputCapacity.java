package com.silvia.apeiron.common.machine.output;

import java.util.List;

import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputBus;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputHatch;
import com.silvia.apeiron.compat.OutputTransactions;

import gregtech.api.interfaces.IOutputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputBusME;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputME;

/** Extends GregTech's ME output preflight with Apeiron's exact unlimited transactions. */
public final class NativeMEOutputCapacity {

    private NativeMEOutputCapacity() {}

    public static boolean hasUnlimitedFluidOutput(MTEMultiBlockBase machine) {
        for (Object hatch : OutputTransactions.hatches(machine)) if (hatch instanceof MTEBoundlessMEOutputHatch) {
            MTEBoundlessMEOutputHatch output = (MTEBoundlessMEOutputHatch) hatch;
            if (!output.isFiltered() && !output.getCheckMode()) return true;
        }
        return false;
    }

    public static boolean canDumpItems(MTEMultiBlockBase machine, List<GTUtility.ItemId> outputs) {
        List<IOutputBus> busses = machine.getOutputBusses();
        if (busses.stream()
            .noneMatch(bus -> bus instanceof MTEBoundlessMEOutputBus)) return false;
        for (GTUtility.ItemId output : outputs) {
            boolean handled = false;
            for (IOutputBus bus : busses) {
                if (bus instanceof MTEHatchOutputBusME) {
                    MTEHatchOutputBusME nativeBus = (MTEHatchOutputBusME) bus;
                    if (!nativeBus.hasPhysicalSpace() || nativeBus.getCheckMode()) continue;
                    if (!nativeBus.isFiltered() || nativeBus.isFilteredToItem(output)) {
                        handled = true;
                        break;
                    }
                }
                if (bus instanceof MTEBoundlessMEOutputBus
                    && ((MTEBoundlessMEOutputBus) bus).isFilteredToItem(output)) {
                    handled = true;
                    break;
                }
            }
            if (!handled) return false;
        }
        return !outputs.isEmpty();
    }

    public static boolean canDumpFluids(MTEMultiBlockBase machine, List<GTUtility.FluidId> outputs) {
        List<?> hatches = OutputTransactions.hatches(machine);
        if (hatches.stream()
            .noneMatch(hatch -> hatch instanceof MTEBoundlessMEOutputHatch)) return false;
        for (GTUtility.FluidId output : outputs) {
            boolean handled = false;
            for (Object hatch : hatches) {
                if (hatch instanceof MTEHatchOutputME) {
                    MTEHatchOutputME nativeHatch = (MTEHatchOutputME) hatch;
                    if (!nativeHatch.hasPhysicalSpace() || nativeHatch.getCheckMode()) continue;
                    if (!nativeHatch.isFiltered() || nativeHatch.isFilteredToFluid(output)) {
                        handled = true;
                        break;
                    }
                }
                if (hatch instanceof MTEBoundlessMEOutputHatch
                    && ((MTEBoundlessMEOutputHatch) hatch).isFilteredToFluid(output)) {
                    handled = true;
                    break;
                }
            }
            if (!handled) return false;
        }
        return !outputs.isEmpty();
    }
}
