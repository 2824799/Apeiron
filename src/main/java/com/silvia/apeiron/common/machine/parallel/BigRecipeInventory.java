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
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.stack.InfiniteAEStack;
import com.silvia.apeiron.api.machine.me.input.BigDualInputHatch;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.input.storage.BigPatternBuffer;
import com.silvia.apeiron.common.machine.me.stocking.StockingInputHost;
import com.silvia.apeiron.common.machine.me.stocking.StockingInputLogic;

import appeng.api.storage.data.IAEFluidStack;
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
    private final int bufferStocks;
    private final List<Integer> physicalStockIndices = new ArrayList<>();

    public BigRecipeInventory(MTEMultiBlockBase machine, ItemStack[] items, FluidStack[] fluids) {
        this(machine, null, items, fluids);
    }

    public BigRecipeInventory(MTEMultiBlockBase machine, MTEMultiBlockBase sharedInputs, ItemStack[] items,
        FluidStack[] fluids) {
        buffer = findPatternBuffer(machine, items, fluids);
        if (buffer != null) stocks.addAll(buffer.getStacksBig());
        bufferStocks = stocks.size();
        if (buffer == null || sharedInputs != null) {
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
            Set<Object> shared = Collections.newSetFromMap(new IdentityHashMap<>());
            if (buffer != null && sharedInputs != null) {
                shared.addAll(sharedInputs.getStoredInputs());
                shared.addAll(sharedInputs.getStoredFluids());
            }
            Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (ItemStack item : items)
                if (item != null && item.stackSize > 0 && seen.add(item) && (buffer == null || shared.contains(item))) {
                    addPhysical(item, AEItemStack.create(item), networks);
                }
            for (FluidStack fluid : fluids) if (fluid != null && fluid.amount > 0
                && seen.add(fluid)
                && (buffer == null || shared.contains(fluid))) {
                    addPhysical(fluid, AEFluidStack.create(fluid), networks);
                }
        }
    }

    /** Resolve the active source by view identity, including mirrors, without merging isolated recipes. */
    public static BigPatternBuffer findPatternBuffer(MTEMultiBlockBase machine, ItemStack[] items,
        FluidStack[] fluids) {
        for (IDualInputHatch hatch : machine.mDualInputHatches) if (hatch instanceof BigDualInputHatch) {
            MTEInfinitePatternInputAssembly source = ((BigDualInputHatch) hatch).getInputSource();
            if (source == null) continue;
            for (BigPatternBuffer candidate : source.getBuffers())
                if (candidate.ownsViews(items, fluids)) return candidate;
        }
        return null;
    }

    public List<IAEStack<?>> getStacksBig() {
        return Collections.unmodifiableList(stocks);
    }

    /** Exact fluid requirements for machines whose cycles bypass ProcessingLogic. */
    public boolean consumeFluids(List<IAEFluidStack> required, boolean simulate) {
        BigInteger[] available = new BigInteger[stocks.size()];
        boolean[] renewable = new boolean[stocks.size()];
        for (int i = 0; i < stocks.size(); i++) {
            available[i] = BigAEStackValues.get(stocks.get(i));
            renewable[i] = BigAEStackValues.isInfinite(stocks.get(i));
        }
        BigInteger[] costs = new BigInteger[required.size()];
        boolean[][] matches = new boolean[required.size()][stocks.size()];
        for (int r = 0; r < required.size(); r++) {
            costs[r] = BigAEStackValues.get(required.get(r));
            for (int i = 0; i < stocks.size(); i++) matches[r][i] = stocks.get(i) instanceof IAEFluidStack
                && ((IAEFluidStack) stocks.get(i)).getFluidStack()
                    .isFluidEqual(
                        required.get(r)
                            .getFluidStack());
        }
        BigInteger[] debit = new BigInputAllocation(available, costs, matches, renewable).allocate(BigInteger.ONE);
        if (debit == null) return false;
        if (!simulate) consume(debit);
        return true;
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
        physicalStockIndices.add(stocks.size());
        stocks.add(
            owner == null ? fallback
                : owner.stock(slot)
                    .copy());
        physical.add(view);
        owners.add(owner);
        ownerSlots.add(slot);
    }

    /** Fills long-backed machine tanks with exact per-fluid debits from the shared recipe transaction. */
    public boolean fillFluids(Map<Fluid, Long> stored, Map<Fluid, BigInteger> targets) {
        BigInteger[] debit = new BigInteger[stocks.size()];
        Map<Fluid, Long> next = new LinkedHashMap<>(stored);
        boolean changed = false;
        for (int i = 0; i < stocks.size(); i++) {
            debit[i] = BigInteger.ZERO;
            IAEStack<?> stock = stocks.get(i);
            if (!(stock instanceof IAEFluidStack)) continue;
            Fluid type = ((IAEFluidStack) stock).getFluidStack()
                .getFluid();
            BigInteger target = targets.get(type);
            if (target == null) continue;
            BigInteger current = BigInteger.valueOf(next.getOrDefault(type, 0L));
            BigInteger missing = target.min(BigInteger.valueOf(Long.MAX_VALUE))
                .subtract(current);
            BigInteger amount = BigAEStackValues.isInfinite(stock) ? missing
                : BigAEStackValues.get(stock)
                    .min(missing);
            if (amount.signum() <= 0) continue;
            debit[i] = amount;
            next.put(
                type,
                current.add(amount)
                    .longValueExact());
            changed = true;
        }
        if (!changed) return false;
        consume(debit);
        stored.clear();
        stored.putAll(next);
        return true;
    }

    public void consume(BigInteger[] debit) {
        if (debit.length != stocks.size()) throw new IllegalArgumentException("Input debit size mismatch");
        for (int i = 0; i < debit.length; i++) if (debit[i].signum() < 0 || !BigAEStackValues.isInfinite(stocks.get(i))
            && debit[i].compareTo(BigAEStackValues.get(stocks.get(i))) > 0)
            throw new IllegalArgumentException("Input debit exceeds inventory");
        if (buffer != null) {
            buffer.reconcile();
            for (int i = 0; i < bufferStocks; i++) if (!buffer.getAmountBig(stocks.get(i))
                .equals(BigAEStackValues.get(stocks.get(i))))
                throw new IllegalStateException("Recipe buffer changed during planning");
        }
        for (int i = 0; i < physical.size(); i++) {
            int stockIndex = physicalStockIndices.get(i);
            if (owners.get(i) != null) {
                IAEStack<?> current = owners.get(i)
                    .stock(ownerSlots.get(i));
                if (!BigAEStackValues.get(current)
                    .equals(BigAEStackValues.get(stocks.get(stockIndex))))
                    throw new IllegalStateException("Network input changed during planning");
            } else {
                Object value = physical.get(i);
                int amount = value instanceof ItemStack ? ((ItemStack) value).stackSize : ((FluidStack) value).amount;
                if (!BigInteger.valueOf(amount)
                    .equals(BigAEStackValues.get(stocks.get(stockIndex))))
                    throw new IllegalStateException("Physical input changed during planning");
            }
        }
        Map<StockingInputLogic, List<IAEStack<?>>> networkDebits = new LinkedHashMap<>();
        for (int i = 0; i < physical.size(); i++) {
            int stockIndex = physicalStockIndices.get(i);
            if (owners.get(i) == null || debit[stockIndex].signum() == 0) continue;
            IAEStack<?> request = stocks.get(stockIndex)
                .copy();
            if (request instanceof InfiniteAEStack) ((InfiniteAEStack) request).setInfinite(false);
            BigAEStackValues.set(request, debit[stockIndex]);
            networkDebits.computeIfAbsent(owners.get(i), key -> new ArrayList<>())
                .add(request);
        }
        if (!StockingInputLogic.commit(networkDebits))
            throw new IllegalStateException("Network cannot satisfy exact input debit");
        if (buffer != null)
            for (int i = 0; i < bufferStocks; i++) if (debit[i].signum() > 0) buffer.removeBig(stocks.get(i), debit[i]);
        for (int i = 0; i < physical.size(); i++) {
            BigInteger amount = debit[physicalStockIndices.get(i)];
            if (owners.get(i) != null) owners.get(i)
                .recordCommitted(ownerSlots.get(i), amount);
            else if (physical.get(i) instanceof ItemStack)
                ((ItemStack) physical.get(i)).stackSize -= amount.intValueExact();
            else((FluidStack) physical.get(i)).amount -= amount.intValueExact();
        }
    }
}
