package com.silvia.apeiron.common.machine.output;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.me.output.BigFluidOutputTransaction;
import com.silvia.apeiron.api.machine.me.output.BigItemOutputTransaction;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.interfaces.IOutputBus;
import gregtech.api.interfaces.IOutputBusTransaction;
import gregtech.api.interfaces.IOutputHatch;
import gregtech.api.interfaces.IOutputHatchTransaction;
import gregtech.api.interfaces.IOutputTransaction;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputBusME;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputME;

/** Full output-set preflight: native receivers stay finite, Apeiron receivers use their exact transactions. */
public final class BigRecipeOutputCapacity {

    private static final BigInteger MAX_INT = BigInteger.valueOf(Integer.MAX_VALUE);

    private BigRecipeOutputCapacity() {}

    private static void configure(IOutputTransaction<?, ?> tx) {
        if (tx instanceof IOutputTransaction.IRecipeCheckAware)
            ((IOutputTransaction.IRecipeCheckAware) tx).setRecipeCheck(true);
        if (tx instanceof IOutputTransaction.IProtectOutputAware)
            ((IOutputTransaction.IProtectOutputAware) tx).setProtectOutput(true);
    }

    public static boolean fits(MTEMultiBlockBase machine, List<IAEStack<?>> outputs) {
        List<Consumer<IAEItemStack>> items = new ArrayList<>();
        List<Consumer<IAEFluidStack>> fluids = new ArrayList<>();
        for (IOutputBus bus : machine.getOutputBusses()) {
            IOutputBusTransaction tx = bus.createTransaction();
            configure(tx);
            if (tx instanceof BigItemOutputTransaction) {
                items.add(s -> ((BigItemOutputTransaction) tx).storePartialBig(s, BigInteger.ONE, BigInteger.ONE));
            } else if (bus instanceof MTEHatchOutputBusME) {
                MTEHatchOutputBusME me = (MTEHatchOutputBusME) bus;
                NativeMEOutputBudget<IAEItemStack> budget = new NativeMEOutputBudget<>(
                    me.getProvider(),
                    me.getActionSource(),
                    () -> me.getCellStack() == null ? null
                        : AEApi.instance()
                            .registries()
                            .cell()
                            .getCellInventory(
                                me.getCellStack()
                                    .copy(),
                                me.getISaveProvider(),
                                me.getChannel()));
                items.add(
                    s -> BigAEStackValues.set(
                        s,
                        BigAEStackValues.get(s)
                            .subtract(budget.reserve(s, BigAEStackValues.get(s)))));
            } else items.add(s -> {
                ItemStack stack = BigAEStackValues.copyWithSize(s, BigInteger.ONE)
                    .getItemStack();
                GTUtility.ItemId id = GTUtility.ItemId.createNoCopy(stack);
                if (tx.isFiltered() && !tx.isFilteredTo(id)) return;
                BigInteger remaining = BigAEStackValues.get(s);
                for (int attempts = 0; attempts < 4096 && remaining.signum() > 0
                    && tx.hasAvailableSpace(); attempts++) {
                    int offered = remaining.min(MAX_INT)
                        .intValue();
                    stack.stackSize = offered;
                    tx.storePartial(id, stack, 1L, 1L);
                    int inserted = offered - stack.stackSize;
                    if (inserted <= 0) break;
                    remaining = remaining.subtract(BigInteger.valueOf(inserted));
                }
                tx.complete(id);
                BigAEStackValues.set(s, remaining);
            });
        }
        for (IOutputHatch hatch : machine.getOutputHatches()) {
            IOutputHatchTransaction tx = hatch.createTransaction();
            configure(tx);
            if (tx instanceof BigFluidOutputTransaction) {
                fluids.add(s -> ((BigFluidOutputTransaction) tx).storePartialBig(s, BigInteger.ONE, BigInteger.ONE));
            } else if (hatch instanceof MTEHatchOutputME) {
                MTEHatchOutputME me = (MTEHatchOutputME) hatch;
                NativeMEOutputBudget<IAEFluidStack> budget = new NativeMEOutputBudget<>(
                    me.getProvider(),
                    me.getActionSource(),
                    () -> me.getCellStack() == null ? null
                        : AEApi.instance()
                            .registries()
                            .cell()
                            .getCellInventory(
                                me.getCellStack()
                                    .copy(),
                                me.getISaveProvider(),
                                me.getChannel()));
                fluids.add(
                    s -> BigAEStackValues.set(
                        s,
                        BigAEStackValues.get(s)
                            .subtract(budget.reserve(s, BigAEStackValues.get(s)))));
            } else fluids.add(s -> {
                FluidStack stack = BigAEStackValues.copyWithSize(s, BigInteger.ONE)
                    .getFluidStack();
                GTUtility.FluidId id = GTUtility.FluidId.create(stack);
                if (tx.isFiltered() && !tx.isFilteredTo(id)) return;
                BigInteger remaining = BigAEStackValues.get(s);
                for (int attempts = 0; attempts < 4096 && remaining.signum() > 0
                    && tx.hasAvailableSpace(); attempts++) {
                    int offered = remaining.min(MAX_INT)
                        .intValue();
                    stack.amount = offered;
                    tx.storePartial(id, stack, 1L, 1L);
                    int inserted = offered - stack.amount;
                    if (inserted <= 0) break;
                    remaining = remaining.subtract(BigInteger.valueOf(inserted));
                }
                tx.complete(id);
                BigAEStackValues.set(s, remaining);
            });
        }
        BigMachineOutputQueue merged = new BigMachineOutputQueue();
        List<IAEStack<?>> all = new ArrayList<>(
            ((com.silvia.apeiron.api.machine.parallel.BigWirelessController) machine).getWirelessRecipeState()
                .pending()
                .snapshotOutputs());
        all.addAll(outputs);
        for (IAEStack<?> stack : all) {
            if (stack instanceof IAEItemStack)
                merged.addItem(((IAEItemStack) stack).getItemStack(), BigAEStackValues.get(stack));
            else merged.addFluid(((IAEFluidStack) stack).getFluidStack(), BigAEStackValues.get(stack));
        }
        all = merged.snapshotOutputs();
        for (IAEStack<?> output : all) {
            if (output instanceof IAEItemStack)
                for (Consumer<IAEItemStack> target : items) target.accept((IAEItemStack) output);
            else for (Consumer<IAEFluidStack> target : fluids) target.accept((IAEFluidStack) output);
            if (BigAEStackValues.get(output)
                .signum() > 0) return false;
        }
        return true;
    }
}
