package com.silvia.apeiron.common.machine.output;

import java.math.BigInteger;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.compat.OutputTransactions;

import gregtech.api.interfaces.IOutputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Native power and recipe logic can use exact output transactions without an Apeiron energy hatch. */
public final class NativeOutputBatches {

    private NativeOutputBatches() {}

    public static boolean hasExactItems(MTEMultiBlockBase machine) {
        for (IOutputBus bus : machine.getOutputBusses()) if (OutputTransactions.items(bus)
            .hasExactItems()) return true;
        return false;
    }

    public static boolean hasExactFluids(MTEMultiBlockBase machine) {
        for (Object hatch : OutputTransactions.hatches(machine)) if (OutputTransactions.fluids(hatch)
            .hasExactFluids()) return true;
        return false;
    }

    public static void completeItems(MTEMultiBlockBase machine, ItemStack[] outputs) {
        BigMachineOutputQueue pending = pending(machine);
        for (ItemStack output : outputs)
            if (output != null && output.stackSize > 0) pending.addItem(output, BigInteger.valueOf(output.stackSize));
        flush(machine, pending);
    }

    public static void completeFluids(MTEMultiBlockBase machine, FluidStack[] outputs) {
        BigMachineOutputQueue pending = pending(machine);
        for (FluidStack output : outputs)
            if (output != null && output.amount > 0) pending.addFluid(output, BigInteger.valueOf(output.amount));
        flush(machine, pending);
    }

    private static BigMachineOutputQueue pending(MTEMultiBlockBase machine) {
        return ((BigWirelessController) machine).getWirelessRecipeState()
            .pending();
    }

    private static void flush(MTEMultiBlockBase machine, BigMachineOutputQueue pending) {
        pending.flush(
            machine.getOutputBusses(),
            OutputTransactions.hatches(machine),
            machine.protectsExcessItem(),
            machine.protectsExcessFluid());
        machine.markDirty();
    }
}
