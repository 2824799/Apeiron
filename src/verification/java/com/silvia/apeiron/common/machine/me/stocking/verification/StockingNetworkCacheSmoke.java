package com.silvia.apeiron.common.machine.me.stocking.verification;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.Iterator;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.stack.InfiniteAEStack;
import com.silvia.apeiron.ae.storage.BigMEInventories;
import com.silvia.apeiron.ae.storage.BigMEInventory;
import com.silvia.apeiron.common.machine.me.stocking.StockingFilter;
import com.silvia.apeiron.common.machine.me.stocking.StockingInputLogic;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.NetworkMonitor;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEFluidStackType;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;

/** Measures shared native monitor scans and verifies projections never mutate the cached network inventory. */
@SuppressWarnings({ "rawtypes", "unchecked" })
public final class StockingNetworkCacheSmoke {

    private StockingNetworkCacheSmoke() {}

    public static void verify() {
        CachedNetwork<IAEItemStack> items = new CachedNetwork<>(AEItemStackType.ITEM_STACK_TYPE);
        CachedNetwork<IAEFluidStack> fluids = new CachedNetwork<>(AEFluidStackType.FLUID_STACK_TYPE);
        IAEItemStack diamond = AEItemStack.create(new ItemStack(Items.diamond));
        IAEFluidStack water = AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1));
        BigInteger huge = BigInteger.TEN.pow(60);
        items.stored.addStorage(BigAEStackValues.copyWithSize(diamond, huge));
        fluids.stored.addStorage(BigAEStackValues.copyWithSize(water, huge));
        IAEItemStack infinite = AEItemStack.create(new ItemStack(Items.paper));
        ((InfiniteAEStack) infinite).setInfinite(true);
        items.stored.addStorage(infinite);
        IAEItemStack craftable = AEItemStack.create(new ItemStack(Items.emerald));
        craftable.setStackSize(0);
        craftable.setCraftable(true);
        items.stored.addCrafting(craftable);
        for (int i = 0; i < 1000; i++) {
            ItemStack stack = new ItemStack(Items.stick);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("entry", i);
            stack.setTagCompound(tag);
            items.stored.addStorage(AEItemStack.create(stack));
        }
        items.invalidate();
        fluids.invalidate();
        CachedInput empty = new CachedInput(items, fluids, StockingInputLogic.Kind.MIXED);
        empty.refresh();
        check(items.scans == 0 && fluids.scans == 0, "unconfigured input scanned ME inventory");
        CachedInput first = new CachedInput(items, fluids, StockingInputLogic.Kind.MIXED);
        CachedInput second = new CachedInput(items, fluids, StockingInputLogic.Kind.ITEMS);
        CachedInput hatch = new CachedInput(items, fluids, StockingInputLogic.Kind.FLUIDS);
        first.setMark(0, diamond);
        first.setMark(359, water);
        second.setMark(0, diamond);
        hatch.setMark(0, water);
        for (int tick = 0; tick <= 1000; tick += 100) {
            first.tick(tick);
            second.tick(tick);
            hatch.tick(tick);
        }
        check(items.scans == 1 && fluids.scans == 1, "unchanged network was rescanned for each input or refresh");
        check(items.visited == 0 && fluids.visited == 0, "manual selections enumerated the cached network list");
        check(
            BigAEStackValues.get(first.getDisplayed(0))
                .equals(huge),
            "item cache truncated exact quantity");
        check(
            BigAEStackValues.get(first.getDisplayed(359))
                .equals(huge),
            "fluid cache truncated exact quantity");
        first.begin();
        first.itemView(0).stackSize -= 7;
        first.fluidViews()[0].amount -= 13;
        check(
            first.end()
                .wasSuccessful(),
            "cached projection failed real network debit");
        check(
            BigAEStackValues.get(second.getDisplayed(0))
                .equals(huge),
            "one input mutated another input's projection");
        first.refresh();
        second.refresh();
        hatch.refresh();
        check(items.scans == 2 && fluids.scans == 2, "network debits did not invalidate shared native caches");
        check(
            BigAEStackValues.get(second.getDisplayed(0))
                .equals(huge.subtract(BigInteger.valueOf(7))),
            "refreshed input retained pre-debit item stock");
        check(
            BigAEStackValues.get(hatch.getDisplayed(0))
                .equals(huge.subtract(BigInteger.valueOf(13))),
            "refreshed hatch retained pre-debit fluid stock");
        BigMEInventories.injectItemsBig(
            items.monitor,
            BigAEStackValues.copyWithSize(diamond, BigInteger.TEN),
            Actionable.MODULATE,
            new BaseActionSource());
        second.refresh();
        check(
            items.scans == 3 && BigAEStackValues.get(second.getDisplayed(0))
                .equals(huge.add(BigInteger.valueOf(3))),
            "network insert did not refresh cached quantities");
        first.getFilter()
            .setItemId("paper");
        first.setAutoPull(true);
        check(BigAEStackValues.isInfinite(first.getDisplayed(0)), "cached auto-selection lost infinite flag");
        first.getFilter()
            .setItemId("emerald");
        first.refresh();
        check(first.getDisplayed(0) == null, "auto-selection supplied craftable-only inventory as stock");
        items.visited = 0;
        second.setAutoPull(true);
        check(second.getDisplayed(359) != null, "automatic selection failed to populate 360 slots");
        check(items.visited <= 362, "automatic selection walked the network after all slots were filled");
        check(items.scans == 3 && fluids.scans == 2, "filter edits rescanned unchanged ME inventories");
        second.connected = false;
        second.refresh();
        check(second.getDisplayed(0) == null, "disconnected input retained visible stock");
        items.stored.resetStatus();
        items.invalidate();
        first.refresh();
        check(first.getDisplayed(0) == null, "removed network inventory remained available in projection");
        verifyAutomaticStrategies();
        Apeiron.LOG.info(
            "Stocking cache verification passed: shared scans, bounded selection, all three input kinds, exact stock ranges/priorities/type caps, live invalidation and detached recipe projections");
    }

    private static void verifyAutomaticStrategies() {
        CachedNetwork<IAEItemStack> items = new CachedNetwork<>(AEItemStackType.ITEM_STACK_TYPE);
        CachedNetwork<IAEFluidStack> fluids = new CachedNetwork<>(AEFluidStackType.FLUID_STACK_TYPE);
        IAEItemStack diamond = AEItemStack.create(new ItemStack(Items.diamond));
        IAEItemStack apple = AEItemStack.create(new ItemStack(Items.apple));
        IAEItemStack paper = AEItemStack.create(new ItemStack(Items.paper));
        ((InfiniteAEStack) paper).setInfinite(true);
        IAEFluidStack water = AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1));
        IAEFluidStack lava = AEFluidStack.create(new FluidStack(FluidRegistry.LAVA, 1));
        BigInteger huge = BigInteger.TEN.pow(80)
            .add(BigInteger.valueOf(17));
        items.stored.addStorage(BigAEStackValues.copyWithSize(diamond, BigInteger.valueOf(64)));
        items.stored.addStorage(BigAEStackValues.copyWithSize(apple, huge));
        items.stored.addStorage(paper);
        fluids.stored.addStorage(BigAEStackValues.copyWithSize(water, huge.add(BigInteger.ONE)));
        fluids.stored.addStorage(BigAEStackValues.copyWithSize(lava, BigInteger.valueOf(64)));
        for (int i = 1; i <= 1000; i++) {
            ItemStack stick = new ItemStack(Items.stick);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("entry", i);
            stick.setTagCompound(tag);
            items.stored.addStorage(BigAEStackValues.copyWithSize(AEItemStack.create(stick), BigInteger.valueOf(i)));
        }
        items.invalidate();
        fluids.invalidate();
        for (StockingInputLogic.Kind kind : StockingInputLogic.Kind.values()) {
            CachedInput manual = new CachedInput(items, fluids, kind);
            IAEStack<?> mark = kind == StockingInputLogic.Kind.FLUIDS ? water : apple;
            manual.setMark(359, mark);
            manual.getFilter()
                .setTypeLimit(1);
            manual.getFilter()
                .setMaximum("1");
            manual.refresh();
            check(
                StockingInputLogic.same(manual.getDisplayed(359), mark),
                "automatic rules pruned a manual selection for " + kind);
            CachedInput input = new CachedInput(items, fluids, kind);
            StockingFilter filter = input.getFilter();
            filter.setTypeLimit(1);
            filter.setPriority(StockingFilter.Priority.MOST);
            input.setAutoPull(true);
            check(input.getDisplayed(1) == null && input.getDisplayed(359) == null, "one-type cap leaked later slots");
            check(
                kind == StockingInputLogic.Kind.FLUIDS ? StockingInputLogic.same(input.getDisplayed(0), water)
                    : BigAEStackValues.isInfinite(input.getDisplayed(0)),
                "most-stock priority failed for " + kind);
            filter.setMaximum(
                huge.add(BigInteger.ONE)
                    .toString());
            input.changed();
            check(
                StockingInputLogic.same(input.getDisplayed(0), kind == StockingInputLogic.Kind.ITEMS ? apple : water),
                "exact quantity ordering failed after excluding infinite stock for " + kind);
            filter.setPriority(StockingFilter.Priority.LEAST);
            input.changed();
            check(
                BigAEStackValues.get(input.getDisplayed(0))
                    .equals(BigInteger.valueOf(kind == StockingInputLogic.Kind.FLUIDS ? 64 : 1)),
                "least-stock priority failed for " + kind);
            filter.setMinimum("64");
            filter.setMaximum("64");
            filter.setTypeLimit(360);
            input.changed();
            int count = 0;
            for (int slot = 0; slot < 360; slot++) if (input.getDisplayed(slot) != null) {
                check(
                    BigAEStackValues.get(input.getDisplayed(slot))
                        .equals(BigInteger.valueOf(64)),
                    "inclusive stock range selected an out-of-range type");
                count++;
            }
            check(
                count == (kind == StockingInputLogic.Kind.ITEMS ? 2 : kind == StockingInputLogic.Kind.FLUIDS ? 1 : 3),
                "stock boundary or input-kind filtering failed");
            IAEStack<?>[] tied = new IAEStack<?>[count];
            for (int i = 0; i < count; i++) tied[i] = input.getDisplayed(i)
                .copy();
            input.refresh();
            for (int i = 0; i < count; i++) check(
                StockingInputLogic.same(tied[i], input.getDisplayed(i)),
                "equal-quantity types reordered across unchanged refreshes");
            filter.setMinimum(huge.toString());
            filter.setMaximum(huge.toString());
            input.changed();
            check(
                kind == StockingInputLogic.Kind.FLUIDS ? input.getDisplayed(0) == null
                    : StockingInputLogic.same(input.getDisplayed(0), apple) && input.getDisplayed(1) == null,
                "large exact stock bounds were rounded or truncated");
            filter.setMaximum(
                huge.add(BigInteger.ONE)
                    .toString());
            filter.setTypeLimit(1);
            filter.setPriority(StockingFilter.Priority.MOST);
            input.changed();
            NBTTagCompound saved = new NBTTagCompound();
            input.save(saved);
            CachedInput restored = new CachedInput(items, fluids, kind);
            restored.load(saved);
            restored.refresh();
            check(
                restored.isAutoPull() && restored.getFilter()
                    .getTypeLimit() == 1
                    && restored.getFilter()
                        .getPriority() == StockingFilter.Priority.MOST
                    && restored.getFilter()
                        .getMaximumText()
                        .equals(
                            huge.add(BigInteger.ONE)
                                .toString())
                    && StockingInputLogic.same(input.getDisplayed(0), restored.getDisplayed(0)),
                "automatic pull settings lost after save/reload");
            check(
                restored.pasteConfiguration(input.copyConfiguration()),
                "new pull settings failed configuration copy");
            check(
                StockingInputLogic.same(input.getDisplayed(0), restored.getDisplayed(0)),
                "configuration copy changed selected type");
            restored.load(new NBTTagCompound());
            check(
                restored.getFilter()
                    .isDefault() && !restored.isAutoPull(),
                "pasting legacy defaults retained stale pull settings");
        }
        check(
            BigAEStackValues.get(items.stored.findPrecise(apple))
                .equals(huge)
                && BigAEStackValues.get(fluids.stored.findPrecise(water))
                    .equals(huge.add(BigInteger.ONE))
                && BigAEStackValues.isInfinite(items.stored.findPrecise(paper)),
            "selection modified shared ME inventory");
        check(items.scans == 1 && fluids.scans == 1, "new selection rules bypassed shared ME cache");
    }

    private static final class CachedInput extends StockingInputLogic {

        final CachedNetwork<IAEItemStack> items;
        final CachedNetwork<IAEFluidStack> fluids;
        boolean connected = true;

        CachedInput(CachedNetwork<IAEItemStack> items, CachedNetwork<IAEFluidStack> fluids, Kind kind) {
            super(null, kind);
            this.items = items;
            this.fluids = fluids;
        }

        @Override
        protected boolean active() {
            return connected;
        }

        @Override
        protected BaseActionSource source() {
            return new BaseActionSource();
        }

        @Override
        protected IMEInventory network(IAEStack<?> stack) {
            return stack instanceof IAEItemStack ? items.monitor : fluids.monitor;
        }

        @Override
        protected IItemList<IAEItemStack> readNetworkItems() {
            return items.snapshot();
        }

        @Override
        protected IItemList<IAEFluidStack> readNetworkFluids() {
            return fluids.snapshot();
        }
    }

    private static final class CachedNetwork<T extends IAEStack<T>> {

        final IItemList<T> stored;
        final NetworkMonitor<T> monitor;
        int scans, visited;

        CachedNetwork(IAEStackType<T> type) {
            stored = type.createList();
            IMEInventoryHandler<T> backend = (IMEInventoryHandler<T>) Proxy.newProxyInstance(
                IMEInventoryHandler.class.getClassLoader(),
                new Class<?>[] { IMEInventoryHandler.class, BigMEInventory.class },
                (proxy, method, args) -> {
                    if (method.getName()
                        .equals("getAvailableItems")) {
                        scans++;
                        IItemList<T> list = (IItemList<T>) args[0];
                        for (T stack : stored) list.add(stack);
                        return list;
                    }
                    if (method.getName()
                        .equals("extractItemsBig")) {
                        T request = (T) args[0];
                        T available = stored.findPrecise(request);
                        if (available == null) return null;
                        BigInteger taken = BigAEStackValues.get(available)
                            .min(BigAEStackValues.get(request));
                        if (args[1] == Actionable.MODULATE) BigAEStackValues.set(
                            available,
                            BigAEStackValues.get(available)
                                .subtract(taken));
                        return BigAEStackValues.copyWithSize(request, taken);
                    }
                    if (method.getName()
                        .equals("injectItemsBig")) {
                        if (args[1] == Actionable.MODULATE) stored.addStorage((T) args[0]);
                        return null;
                    }
                    return null;
                });
            Object flow = com.silvia.apeiron.verification.FlowStatisticsFixture.create();
            IGrid grid = (IGrid) Proxy.newProxyInstance(
                IGrid.class.getClassLoader(),
                new Class<?>[] { IGrid.class },
                (proxy, method, args) -> {
                    if (method.getName()
                        .equals("getCache")) return flow;
                    if (method.getName()
                        .equals("postEvent")) return args[0];
                    return null;
                });
            monitor = new NetworkMonitor<T>(new GridStorageCache(grid), type) {

                @Override
                public IMEInventoryHandler<T> getHandler() {
                    return backend;
                }
            };
        }

        IItemList<T> snapshot() {
            IItemList<T> list = monitor.getStorageList();
            return (IItemList<T>) Proxy.newProxyInstance(
                IItemList.class.getClassLoader(),
                new Class<?>[] { IItemList.class },
                (proxy, method, args) -> {
                    if (method.getName()
                        .equals("iterator")) {
                        Iterator<T> iterator = list.iterator();
                        return new Iterator<T>() {

                            @Override
                            public boolean hasNext() {
                                return iterator.hasNext();
                            }

                            @Override
                            public T next() {
                                visited++;
                                return iterator.next();
                            }
                        };
                    }
                    return method.invoke(list, args);
                });
        }

        void invalidate() {
            try {
                java.lang.reflect.Method force = NetworkMonitor.class.getDeclaredMethod("forceUpdate");
                force.setAccessible(true);
                force.invoke(monitor);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Stocking cache verification: " + message);
    }
}
