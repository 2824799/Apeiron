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

import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.tileentities.machines.IDualInputHatch;

/** One authoritative inventory snapshot and atomic input debit shared by every exact recipe path. */
public final class BigRecipeInventory {

    private final List<IAEStack<?>> stocks = new ArrayList<>();
    private final List<Object> physical = new ArrayList<>();
    private final List<StockingInputLogic> owners = new ArrayList<>();
    private final List<Integer> ownerSlots = new ArrayList<>();
    private final BigPatternBuffer buffer;

    public BigRecipeInventory(MTEMultiBlockBase machine, ItemStack[] items, FluidStack[] fluids) {
        this(machine, null, items, fluids);
    }

    public BigRecipeInventory(MTEMultiBlockBase machine, MTEMultiBlockBase sharedInputs, ItemStack[] items,
        FluidStack[] fluids) {
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
            if (sharedInputs != null) {
                hatches.addAll(sharedInputs.mInputBusses);
                hatches.addAll(sharedInputs.mInputHatches);
                hatches.addAll(sharedInputs.mDualInputHatches);
            }
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
    }

    public List<IAEStack<?>> getStacksBig() {
        return Collections.unmodifiableList(stocks);
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

    public void consume(BigInteger[] debit) {
        if (debit.length != stocks.size()) throw new IllegalArgumentException("Input debit size mismatch");
        for (int i = 0; i < debit.length; i++) if (debit[i].signum() < 0 || !BigAEStackValues.isInfinite(stocks.get(i))
            && debit[i].compareTo(BigAEStackValues.get(stocks.get(i))) > 0)
            throw new IllegalArgumentException("Input debit exceeds inventory");
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
