package com.silvia.apeiron.common.machine.parallel;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.me.input.BigDualInputHatch;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.input.storage.BigPatternBuffer;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.common.tileentities.machines.IDualInputHatch;

/** Snapshot and exact debit for one isolated recipe inventory; the native recipe keeps its matching rules. */
public final class BigRecipeInputs {

    private final List<IAEStack<?>> stocks = new ArrayList<>();
    private final List<Object> physical = new ArrayList<>();
    private final BigPatternBuffer buffer;
    private final BigInputAllocation allocation;

    public BigRecipeInputs(MTEMultiBlockBase machine, GTRecipe recipe, ItemStack[] items, FluidStack[] fluids) {
        BigPatternBuffer found = null;
        for (IDualInputHatch hatch : machine.mDualInputHatches) if (hatch instanceof BigDualInputHatch) {
            MTEInfinitePatternInputAssembly source = ((BigDualInputHatch) hatch).getInputSource();
            if (source == null) continue;
            for (BigPatternBuffer candidate : source.getBuffers())
                if (candidate.ownsViews(items, fluids)) found = candidate;
        }
        buffer = found;
        if (buffer != null) stocks.addAll(buffer.getStacksBig());
        else {
            java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (ItemStack item : items) if (item != null && item.stackSize > 0 && seen.add(item)) {
                stocks.add(AEItemStack.create(item));
                physical.add(item);
            }
            for (FluidStack fluid : fluids) if (fluid != null && fluid.amount > 0 && seen.add(fluid)) {
                stocks.add(AEFluidStack.create(fluid));
                physical.add(fluid);
            }
        }
        List<BigInteger> costs = new ArrayList<>();
        List<boolean[]> matches = new ArrayList<>();
        for (GTRecipe.RecipeItemInput input : recipe.getCachedCombinedItemInputs()) {
            if (input.inputAmount == 0) {
                boolean present = false;
                for (ItemStack item : items)
                    if (item != null && input.matchesRecipe(GTOreDictUnificator.getAssociation(item), item))
                        present = true;
                if (!present) throw new IllegalArgumentException("Missing catalyst");
                continue;
            }
            boolean[] matching = new boolean[stocks.size()];
            for (int i = 0; i < stocks.size(); i++) if (stocks.get(i) instanceof IAEItemStack) {
                ItemStack type = ((IAEItemStack) stocks.get(i)).getItemStack();
                matching[i] = input.matchesRecipe(GTOreDictUnificator.getAssociation(type), type);
            }
            costs.add(BigInteger.valueOf(input.inputAmount));
            matches.add(matching);
        }
        for (FluidStack input : recipe.mFluidInputs) if (input != null && input.amount > 0) {
            boolean[] matching = new boolean[stocks.size()];
            for (int i = 0; i < stocks.size(); i++) if (stocks.get(i) instanceof IAEFluidStack)
                matching[i] = ((IAEFluidStack) stocks.get(i)).getFluidStack()
                    .isFluidEqual(input);
            costs.add(BigInteger.valueOf(input.amount));
            matches.add(matching);
        }
        BigInteger[] available = stocks.stream()
            .map(BigAEStackValues::get)
            .toArray(BigInteger[]::new);
        allocation = new BigInputAllocation(
            available,
            costs.toArray(new BigInteger[0]),
            matches.toArray(new boolean[0][]));
    }

    public BigInputAllocation allocation() {
        return allocation;
    }

    public void consume(BigInteger parallels) {
        BigInteger[] debit = allocation.allocate(parallels);
        if (debit == null) throw new IllegalStateException("Recipe snapshot cannot satisfy its plan");
        if (buffer != null) {
            buffer.reconcile();
            for (int i = 0; i < stocks.size(); i++) if (!buffer.getAmountBig(stocks.get(i))
                .equals(BigAEStackValues.get(stocks.get(i))))
                throw new IllegalStateException("Recipe buffer changed during planning");
            for (int i = 0; i < stocks.size(); i++)
                if (debit[i].signum() > 0) buffer.removeBig(stocks.get(i), debit[i]);
        } else {
            for (int i = 0; i < physical.size(); i++) {
                Object value = physical.get(i);
                int amount = value instanceof ItemStack ? ((ItemStack) value).stackSize : ((FluidStack) value).amount;
                if (!BigInteger.valueOf(amount)
                    .equals(BigAEStackValues.get(stocks.get(i))))
                    throw new IllegalStateException("Physical input changed during planning");
            }
            for (int i = 0; i < physical.size(); i++) {
                Object value = physical.get(i);
                int amount = debit[i].intValueExact();
                if (value instanceof ItemStack) ((ItemStack) value).stackSize -= amount;
                else((FluidStack) value).amount -= amount;
            }
        }
    }
}
