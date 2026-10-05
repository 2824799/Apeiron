package com.silvia.apeiron.common.machine.output;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.common.machine.me.output.storage.BigCacheCounter;
import com.silvia.apeiron.compat.OutputTransactions;
import com.silvia.apeiron.compat.OutputTransactions.Handle;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.interfaces.IOutputBus;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputBusME;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputME;
import gregtech.common.tileentities.machines.outputme.base.MTEHatchOutputMEBase;

/** Merges exact outputs by type and retains rejected quantities; work on physical outputs is bounded per flush. */
public final class BigMachineOutputQueue {

    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger MAX_INT = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final String ITEM_KEY = "ApeironPendingItemOutputs";
    private static final String FLUID_KEY = "ApeironPendingFluidOutputs";
    private final BigCacheCounter<IAEItemStack> items = new BigCacheCounter<>();
    private final BigCacheCounter<IAEFluidStack> fluids = new BigCacheCounter<>();

    public void addItem(final ItemStack type, final BigInteger amount) {
        if (type == null || amount.signum() == 0) return;
        if (amount.signum() < 0) throw new IllegalArgumentException("Negative item output");
        final ItemStack identity = type.copy();
        identity.stackSize = 1;
        final IAEItemStack key = AEItemStack.create(identity);
        items.insertBig(BigAEStackValues.copyWithSize(key, BigInteger.ZERO), amount);
    }

    public void addFluid(final FluidStack type, final BigInteger amount) {
        if (type == null || amount.signum() == 0) return;
        if (amount.signum() < 0) throw new IllegalArgumentException("Negative fluid output");
        final FluidStack identity = type.copy();
        identity.amount = 1;
        fluids.insertBig(BigAEStackValues.copyWithSize(AEFluidStack.create(identity), BigInteger.ZERO), amount);
    }

    public BigInteger getItemAmountBig() {
        return items.getTotalBig();
    }

    public BigInteger getFluidAmountBig() {
        return fluids.getTotalBig();
    }

    public boolean isEmpty() {
        return items.isEmpty() && fluids.isEmpty();
    }

    /** Display consumers receive detached exact stacks and cannot consume this output ledger. */
    public List<IAEStack<?>> snapshotOutputs() {
        final List<IAEStack<?>> result = new ArrayList<>();
        items.iterateAllBig((type, amount) -> result.add(BigAEStackValues.copyWithSize(type, amount)));
        fluids.iterateAllBig((type, amount) -> result.add(BigAEStackValues.copyWithSize(type, amount)));
        result.sort((left, right) -> {
            final int quantity = BigAEStackValues.get(right)
                .compareTo(BigAEStackValues.get(left));
            return quantity == 0 ? left.getDisplayName()
                .compareTo(right.getDisplayName()) : quantity;
        });
        return result;
    }

    /** HUDs only need a few rows; do not copy and sort the entire ledger for a hover update. */
    public List<IAEStack<?>> previewOutputs(int limit) {
        final List<IAEStack<?>> result = new ArrayList<>();
        if (limit <= 0) return result;
        java.util.function.BiConsumer<IAEStack<?>, BigInteger> select = (type, amount) -> {
            int index = 0;
            while (index < result.size() && BigAEStackValues.get(result.get(index))
                .compareTo(amount) >= 0) index++;
            if (index >= limit) return;
            result.add(index, BigAEStackValues.copyWithSize(type, amount));
            if (result.size() > limit) result.remove(limit);
        };
        items.iterateAllBig(select::accept);
        fluids.iterateAllBig(select::accept);
        return result;
    }

    public int outputTypes() {
        return items.size() + fluids.size();
    }

    public void moveTo(final BigMachineOutputQueue target) {
        if (target == this) throw new IllegalArgumentException("Cannot move a queue into itself");
        items.iterateAllBig((type, amount) -> target.addItem(type.getItemStack(), amount));
        fluids.iterateAllBig((type, amount) -> target.addFluid(type.getFluidStack(), amount));
        items.clear();
        fluids.clear();
    }

    private interface Target<T extends IAEStack<T>> {

        void insert(T request);

        default void commit() {}
    }

    /** A bounded batch across both item and fluid physical inventories. Big ME output never consumes this budget. */
    private static final class Budget {

        int remaining = 64;
    }

    public boolean flush(final List<IOutputBus> busses, final List<?> hatches, final boolean protectItems,
        final boolean protectFluids) {
        final BigInteger beforeItems = items.getTotalBig();
        final BigInteger beforeFluids = fluids.getTotalBig();
        final Budget budget = new Budget();
        if (!items.isEmpty()) flushItems(busses, protectItems, budget);
        if (!fluids.isEmpty()) flushFluids(hatches, protectFluids, budget);
        return !items.getTotalBig()
            .equals(beforeItems)
            || !fluids.getTotalBig()
                .equals(beforeFluids);
    }

    private void flushItems(final List<IOutputBus> busses, final boolean protection, final Budget budget) {
        final List<IOutputBus> ordered = new ArrayList<>(busses);
        ordered.sort(
            Comparator.comparingInt(
                bus -> bus.getBusType()
                    .ordinal()));
        final List<Target<IAEItemStack>> big = new ArrayList<>();
        final List<Target<IAEItemStack>> legacy = new ArrayList<>();
        for (final IOutputBus bus : ordered) {
            final Handle transaction = OutputTransactions.items(bus);
            transaction.configure(false, protection);
            if (transaction.hasExactItems()) {
                big.add(new Target<IAEItemStack>() {

                    @Override
                    public void insert(final IAEItemStack request) {
                        transaction.storePartialBig(request, BigInteger.ONE, BigInteger.ONE);
                    }

                    @Override
                    public void commit() {
                        transaction.commit();
                    }
                });
            } else if (bus.getClass() == MTEHatchOutputBusME.class) {
                legacy.add(request -> insertLegacyME(((MTEHatchOutputBusME) bus).getProvider(), request));
            } else {
                legacy.add(new Target<IAEItemStack>() {

                    @Override
                    public void insert(final IAEItemStack request) {
                        BigInteger remaining = BigAEStackValues.get(request);
                        final ItemStack physical = BigAEStackValues.copyWithSize(request, BigInteger.ONE)
                            .getItemStack();
                        final GTUtility.ItemId id = GTUtility.ItemId.create(physical);
                        if (transaction.isFiltered() && !transaction.isFilteredTo(id)) return;
                        while (remaining.signum() > 0 && budget.remaining > 0 && transaction.hasAvailableSpace()) {
                            final int chunk = remaining.min(MAX_INT)
                                .intValueExact();
                            physical.stackSize = chunk;
                            budget.remaining--;
                            transaction.storePartial(id, physical, 1L, 1L);
                            final int inserted = chunk - physical.stackSize;
                            if (inserted <= 0) break;
                            remaining = remaining.subtract(BigInteger.valueOf(inserted));
                        }
                        transaction.complete(id);
                        BigAEStackValues.set(request, remaining);
                    }

                    @Override
                    public void commit() {
                        transaction.commit();
                    }
                });
            }
        }
        big.addAll(legacy);
        transfer(items, big);
    }

    private void flushFluids(final List<?> hatches, final boolean protection, final Budget budget) {
        final List<?> ordered = new ArrayList<>(hatches);
        ordered.sort(Comparator.comparingInt(OutputTransactions::order));
        final List<Target<IAEFluidStack>> big = new ArrayList<>();
        final List<Target<IAEFluidStack>> legacy = new ArrayList<>();
        for (final Object hatch : ordered) {
            final Handle transaction = OutputTransactions.fluids(hatch);
            transaction.configure(false, protection);
            if (transaction.hasExactFluids()) {
                big.add(new Target<IAEFluidStack>() {

                    @Override
                    public void insert(final IAEFluidStack request) {
                        transaction.storePartialBig(request, BigInteger.ONE, BigInteger.ONE);
                    }

                    @Override
                    public void commit() {
                        transaction.commit();
                    }
                });
            } else if (hatch.getClass() == MTEHatchOutputME.class) {
                legacy.add(request -> insertLegacyME(((MTEHatchOutputME) hatch).getProvider(), request));
            } else {
                legacy.add(new Target<IAEFluidStack>() {

                    @Override
                    public void insert(final IAEFluidStack request) {
                        BigInteger remaining = BigAEStackValues.get(request);
                        final FluidStack physical = BigAEStackValues.copyWithSize(request, BigInteger.ONE)
                            .getFluidStack();
                        final GTUtility.FluidId id = GTUtility.FluidId.create(physical);
                        if (transaction.isFiltered() && !transaction.isFilteredTo(id)) return;
                        while (remaining.signum() > 0 && budget.remaining > 0 && transaction.hasAvailableSpace()) {
                            final int chunk = remaining.min(MAX_INT)
                                .intValueExact();
                            physical.amount = chunk;
                            budget.remaining--;
                            transaction.storePartial(id, physical, 1L, 1L);
                            final int inserted = chunk - physical.amount;
                            if (inserted <= 0) break;
                            remaining = remaining.subtract(BigInteger.valueOf(inserted));
                        }
                        transaction.complete(id);
                        BigAEStackValues.set(request, remaining);
                    }

                    @Override
                    public void commit() {
                        transaction.commit();
                    }
                });
            }
        }
        big.addAll(legacy);
        transfer(fluids, big);
    }

    private static <T extends IAEStack<T>> void transfer(final BigCacheCounter<T> cache,
        final List<Target<T>> targets) {
        cache.updateAllBig((key, amount) -> {
            final T request = BigAEStackValues.copyWithSize(key, amount);
            for (final Target<T> target : targets) {
                if (BigAEStackValues.get(request)
                    .signum() == 0) break;
                target.insert(request);
            }
            return BigAEStackValues.get(request);
        });
        targets.forEach(Target::commit);
    }

    private static <T extends IAEStack<T>> void insertLegacyME(final MTEHatchOutputMEBase<T> provider,
        final T request) {
        final BigInteger requested = BigAEStackValues.get(request);
        final long cached = provider.getCachedAmount();
        if (cached < 0) return;
        final BigInteger room = MAX_LONG.subtract(BigInteger.valueOf(cached))
            .max(BigInteger.ZERO);
        final BigInteger chunk = requested.min(room);
        if (chunk.signum() == 0) return;
        final T legacy = BigAEStackValues.copyWithSize(request, chunk);
        provider.storePartial(legacy, false);
        final BigInteger remaining = requested.subtract(chunk.subtract(BigAEStackValues.get(legacy)));
        BigAEStackValues.set(request, remaining);
    }

    public void save(final NBTTagCompound tag) {
        final NBTTagList itemList = new NBTTagList();
        items.iterateAllBig((type, amount) -> {
            final NBTTagCompound entry = new NBTTagCompound();
            entry.setTag(
                "Type",
                BigAEStackValues.copyWithSize(type, BigInteger.ONE)
                    .getItemStack()
                    .writeToNBT(new NBTTagCompound()));
            BigValueCodec.writeNBT(entry, "Count", "ExactCount", new AdaptiveInteger(amount));
            itemList.appendTag(entry);
        });
        if (itemList.tagCount() > 0) tag.setTag(ITEM_KEY, itemList);
        else tag.removeTag(ITEM_KEY);
        final NBTTagList fluidList = new NBTTagList();
        fluids.iterateAllBig((type, amount) -> {
            final NBTTagCompound entry = new NBTTagCompound();
            entry.setTag(
                "Type",
                BigAEStackValues.copyWithSize(type, BigInteger.ONE)
                    .getFluidStack()
                    .writeToNBT(new NBTTagCompound()));
            BigValueCodec.writeNBT(entry, "Count", "ExactCount", new AdaptiveInteger(amount));
            fluidList.appendTag(entry);
        });
        if (fluidList.tagCount() > 0) tag.setTag(FLUID_KEY, fluidList);
        else tag.removeTag(FLUID_KEY);
    }

    public void load(final NBTTagCompound tag) {
        items.clear();
        fluids.clear();
        final NBTTagList itemList = tag.getTagList(ITEM_KEY, 10);
        for (int i = 0; i < itemList.tagCount(); i++) {
            final NBTTagCompound entry = itemList.getCompoundTagAt(i);
            addItem(
                ItemStack.loadItemStackFromNBT(entry.getCompoundTag("Type")),
                BigValueCodec.readNBT(entry, "Count", "ExactCount")
                    .toBigInteger());
        }
        final NBTTagList fluidList = tag.getTagList(FLUID_KEY, 10);
        for (int i = 0; i < fluidList.tagCount(); i++) {
            final NBTTagCompound entry = fluidList.getCompoundTagAt(i);
            addFluid(
                FluidStack.loadFluidStackFromNBT(entry.getCompoundTag("Type")),
                BigValueCodec.readNBT(entry, "Count", "ExactCount")
                    .toBigInteger());
        }
    }
}
