package com.silvia.apeiron.common.machine.parallel;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.stack.InfiniteAEStack;
import com.silvia.apeiron.api.machine.me.input.BigDualInputHatch;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.input.storage.BigPatternBuffer;
import com.silvia.apeiron.common.machine.me.stocking.StockingInputHost;
import com.silvia.apeiron.common.machine.me.stocking.StockingInputLogic;

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
    private final List<StockingInputLogic> owners = new ArrayList<>();
    private final List<Integer> ownerSlots = new ArrayList<>();
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
            List<StockingInputLogic> networks = new ArrayList<>();
            List<Object> hatches = new ArrayList<>();
            hatches.addAll(machine.mInputBusses);
            hatches.addAll(machine.mInputHatches);
            hatches.addAll(machine.mDualInputHatches);
            for (Object hatch : hatches)
                if (hatch instanceof StockingInputHost) networks.add(((StockingInputHost) hatch).getStockingInput());
            Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (ItemStack item : items) if (item != null && item.stackSize > 0 && seen.add(item)) {
                addPhysical(item, AEItemStack.create(item), networks);
            }
            for (FluidStack fluid : fluids) if (fluid != null && fluid.amount > 0 && seen.add(fluid)) {
                addPhysical(fluid, AEFluidStack.create(fluid), networks);
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
            matches.toArray(new boolean[0][]),
            renewable());
    }

    private boolean[] renewable() {
        boolean[] result = new boolean[stocks.size()];
        for (int i = 0; i < result.length; i++) result[i] = BigAEStackValues.isInfinite(stocks.get(i));
        return result;
    }

    private void addPhysical(Object view, IAEStack<?> fallback, List<StockingInputLogic> networks) {
        StockingInputLogic owner = null;
        int slot = -1;
        for (StockingInputLogic candidate : networks) {
            int index = candidate.viewIndex(view);
            if (index >= 0) {
                owner = candidate;
                slot = index;
                break;
            }
        }
        stocks.add(
            owner == null ? fallback
                : owner.stock(slot)
                    .copy());
        physical.add(view);
        owners.add(owner);
        ownerSlots.add(slot);
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
                if (owners.get(i) != null) {
                    IAEStack<?> current = owners.get(i)
                        .stock(ownerSlots.get(i));
                    if (!BigAEStackValues.get(current)
                        .equals(BigAEStackValues.get(stocks.get(i))))
                        throw new IllegalStateException("Network input changed during planning");
                    continue;
                }
                Object value = physical.get(i);
                int amount = value instanceof ItemStack ? ((ItemStack) value).stackSize : ((FluidStack) value).amount;
                if (!BigInteger.valueOf(amount)
                    .equals(BigAEStackValues.get(stocks.get(i))))
                    throw new IllegalStateException("Physical input changed during planning");
            }
            Map<StockingInputLogic, List<IAEStack<?>>> networkDebits = new LinkedHashMap<>();
            for (int i = 0; i < physical.size(); i++) if (owners.get(i) != null && debit[i].signum() > 0) {
                IAEStack<?> request = stocks.get(i)
                    .copy();
                if (request instanceof InfiniteAEStack) ((InfiniteAEStack) request).setInfinite(false);
                BigAEStackValues.set(request, debit[i]);
                networkDebits.computeIfAbsent(owners.get(i), key -> new ArrayList<>())
                    .add(request);
            }
            if (!StockingInputLogic.commit(networkDebits))
                throw new IllegalStateException("Network cannot satisfy exact input debit");
            for (int i = 0; i < physical.size(); i++) {
                if (owners.get(i) != null) {
                    owners.get(i)
                        .recordCommitted(ownerSlots.get(i), debit[i]);
                    continue;
                }
                Object value = physical.get(i);
                int amount = debit[i].intValueExact();
                if (value instanceof ItemStack) ((ItemStack) value).stackSize -= amount;
                else((FluidStack) value).amount -= amount;
            }
        }
    }
}
