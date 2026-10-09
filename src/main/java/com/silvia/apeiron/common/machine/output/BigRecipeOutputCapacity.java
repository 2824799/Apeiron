package com.silvia.apeiron.common.machine.output;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.compat.OutputTransactions;
import com.silvia.apeiron.compat.OutputTransactions.Handle;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.interfaces.IOutputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputBusME;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputME;

/** Full output-set preflight: native receivers stay finite, Apeiron receivers use their exact transactions. */
public final class BigRecipeOutputCapacity {

    private static final BigInteger MAX_INT = BigInteger.valueOf(Integer.MAX_VALUE);

    private BigRecipeOutputCapacity() {}

    public static boolean fits(MTEMultiBlockBase machine, List<IAEStack<?>> outputs) {
        return check(machine, outputs).wasSuccessful();
    }

    /** Identifies the channel that cannot accept the outputs without committing either transaction. */
    public static CheckRecipeResult check(MTEMultiBlockBase machine, List<IAEStack<?>> outputs) {
        List<Consumer<IAEItemStack>> items = new ArrayList<>();
        List<Consumer<IAEFluidStack>> fluids = new ArrayList<>();
        for (IOutputBus bus : machine.getOutputBusses()) {
            Handle tx = OutputTransactions.items(bus);
            tx.configure(true, true);
            if (tx.hasExactItems()) {
                items.add(s -> tx.storePartialBig(s, BigInteger.ONE, BigInteger.ONE));
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
        for (Object hatch : OutputTransactions.hatches(machine)) {
            Handle tx = OutputTransactions.fluids(hatch);
            tx.configure(true, true);
            if (tx.hasExactFluids()) {
                fluids.add(s -> tx.storePartialBig(s, BigInteger.ONE, BigInteger.ONE));
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
                .snapshotOutputsUnsorted());
        all.addAll(outputs);
        for (IAEStack<?> stack : all) {
            if (stack instanceof IAEItemStack)
                merged.addItem(((IAEItemStack) stack).getItemStack(), BigAEStackValues.get(stack));
            else merged.addFluid(((IAEFluidStack) stack).getFluidStack(), BigAEStackValues.get(stack));
        }
        all = merged.snapshotOutputsUnsorted();
        // Preserve largest-first allocation for finite receivers without resolving localized display names.
        all.sort(
            (left, right) -> BigAEStackValues.get(right)
                .compareTo(BigAEStackValues.get(left)));
        for (IAEStack<?> output : all) {
            if (output instanceof IAEItemStack)
                for (Consumer<IAEItemStack> target : items) target.accept((IAEItemStack) output);
            else for (Consumer<IAEFluidStack> target : fluids) target.accept((IAEFluidStack) output);
            if (BigAEStackValues.get(output)
                .signum() > 0)
                return output instanceof IAEItemStack ? CheckRecipeResultRegistry.ITEM_OUTPUT_FULL
                    : CheckRecipeResultRegistry.FLUID_OUTPUT_FULL;
        }
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }
}
