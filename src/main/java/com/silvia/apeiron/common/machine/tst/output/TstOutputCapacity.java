package com.silvia.apeiron.common.machine.tst.output;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase.FluidStackLong;
import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase.ItemStackLong;
import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.processingLogics.TSTMEOutputTransaction;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.me.output.BigFluidOutputTransaction;
import com.silvia.apeiron.api.machine.me.output.BigItemOutputTransaction;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.interfaces.IOutputBus;
import gregtech.api.interfaces.IOutputBusTransaction;
import gregtech.api.interfaces.IOutputHatch;
import gregtech.api.interfaces.IOutputHatchTransaction;
import gregtech.api.interfaces.IOutputTransaction;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputBusME;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputME;

/** Checks the entire set against transaction copies. Reservations are never committed. */
public final class TstOutputCapacity {

    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

    private TstOutputCapacity() {}

    private static void configure(IOutputTransaction<?, ?> transaction) {
        if (transaction instanceof IOutputTransaction.IRecipeCheckAware)
            ((IOutputTransaction.IRecipeCheckAware) transaction).setRecipeCheck(true);
        if (transaction instanceof IOutputTransaction.IProtectOutputAware)
            ((IOutputTransaction.IProtectOutputAware) transaction).setProtectOutput(true);
    }

    public static boolean itemsFit(List<ItemStackLong> outputs, List<IOutputBus> busses) {
        if (outputs == null || outputs.isEmpty()) return true;
        final List<BiFunction<ItemStack, BigInteger, BigInteger>> targets = new ArrayList<>();
        final List<BiFunction<ItemStack, BigInteger, BigInteger>> legacyTargets = new ArrayList<>();
        final List<IOutputBus> ordered = new ArrayList<>(busses);
        ordered.sort(
            Comparator.comparingInt(
                bus -> bus.getBusType()
                    .ordinal()));
        for (IOutputBus bus : ordered) {
            final IOutputBusTransaction transaction = bus.createTransaction();
            if (transaction instanceof BigItemOutputTransaction) {
                configure(transaction);
                targets.add((type, amount) -> {
                    final IAEItemStack request = BigAEStackValues.copyWithSize(AEItemStack.create(type), amount);
                    ((BigItemOutputTransaction) transaction).storePartialBig(request, BigInteger.ONE, BigInteger.ONE);
                    return amount.subtract(BigAEStackValues.get(request));
                });
            } else if (bus instanceof MTEHatchOutputBusME) {
                final TSTMEOutputTransaction<?, ItemStack, IAEItemStack> legacy = TSTMEOutputTransaction
                    .forItems((MTEHatchOutputBusME) bus, true, true);
                legacyTargets.add(
                    (type, amount) -> BigInteger.valueOf(
                        legacy.reserve(
                            type,
                            amount.min(MAX_LONG)
                                .longValueExact())));
            }
        }
        targets.addAll(legacyTargets);
        for (ItemStackLong entry : outputs) {
            BigInteger remaining = BigTstOutputLists.amount(entry);
            if (entry.itemStack() == null || remaining.signum() <= 0) continue;
            for (BiFunction<ItemStack, BigInteger, BigInteger> target : targets) {
                remaining = remaining.subtract(target.apply(entry.itemStack(), remaining));
                if (remaining.signum() == 0) break;
            }
            if (remaining.signum() > 0) return false;
        }
        return true;
    }

    public static boolean fluidsFit(List<FluidStackLong> outputs, List<IOutputHatch> hatches) {
        if (outputs == null || outputs.isEmpty()) return true;
        final List<BiFunction<FluidStack, BigInteger, BigInteger>> targets = new ArrayList<>();
        final List<BiFunction<FluidStack, BigInteger, BigInteger>> legacyTargets = new ArrayList<>();
        final List<IOutputHatch> ordered = new ArrayList<>(hatches);
        ordered.sort(
            Comparator.comparingInt(
                hatch -> hatch.getHatchType()
                    .ordinal()));
        for (IOutputHatch hatch : ordered) {
            final IOutputHatchTransaction transaction = hatch.createTransaction();
            if (transaction instanceof BigFluidOutputTransaction) {
                configure(transaction);
                targets.add((type, amount) -> {
                    final IAEFluidStack request = BigAEStackValues.copyWithSize(AEFluidStack.create(type), amount);
                    ((BigFluidOutputTransaction) transaction).storePartialBig(request, BigInteger.ONE, BigInteger.ONE);
                    return amount.subtract(BigAEStackValues.get(request));
                });
            } else if (hatch instanceof MTEHatchOutputME) {
                final TSTMEOutputTransaction<?, FluidStack, IAEFluidStack> legacy = TSTMEOutputTransaction
                    .forFluids((MTEHatchOutputME) hatch, true, true);
                legacyTargets.add(
                    (type, amount) -> BigInteger.valueOf(
                        legacy.reserve(
                            type,
                            amount.min(MAX_LONG)
                                .longValueExact())));
            }
        }
        targets.addAll(legacyTargets);
        for (FluidStackLong entry : outputs) {
            BigInteger remaining = BigTstOutputLists.amount(entry);
            if (entry.fluidStack() == null || remaining.signum() <= 0) continue;
            for (BiFunction<FluidStack, BigInteger, BigInteger> target : targets) {
                remaining = remaining.subtract(target.apply(entry.fluidStack(), remaining));
                if (remaining.signum() == 0) break;
            }
            if (remaining.signum() > 0) return false;
        }
        return true;
    }
}
