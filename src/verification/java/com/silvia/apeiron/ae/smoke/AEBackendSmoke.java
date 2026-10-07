package com.silvia.apeiron.ae.smoke;

import java.lang.reflect.Proxy;
import java.math.BigInteger;

import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigCellInventory;
import com.silvia.apeiron.ae.storage.BigCellInventoryHandler;
import com.silvia.apeiron.ae.storage.BigInventoryAdaptors;
import com.silvia.apeiron.ae.storage.BigMEInventories;
import com.silvia.apeiron.ae.storage.BigStorageCell;
import com.silvia.apeiron.ae.storage.StorageCellMounts;

import appeng.api.config.Actionable;
import appeng.api.config.InsertionMode;
import appeng.api.config.ReshufflePhase;
import appeng.api.exceptions.AppEngException;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.ReshuffleActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.helpers.ReshuffleTask;
import appeng.items.materials.MaterialType;
import appeng.items.storage.ItemBasicStorageCell;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.NetworkMonitor;
import appeng.me.storage.FluidCellInventory;
import appeng.me.storage.FluidCellInventoryHandler;
import appeng.me.storage.MEIInventoryWrapper;
import appeng.me.storage.MEInventoryHandler;
import appeng.me.storage.MEMonitorIInventory;
import appeng.me.storage.NetworkInventoryHandler;
import appeng.util.AEStackTypeFilter;
import appeng.util.IterationCounter;
import appeng.util.inv.AdaptorIInventory;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEFluidStackType;
import appeng.util.item.AEItemStack;
import appeng.util.item.IAEStackList;

/** In-game regression checks for transformed physical bridges, fluid networks and reshuffle rollback. */
public final class AEBackendSmoke {

    private static final BaseActionSource SOURCE = new BaseActionSource();
    private static final BigInteger HUGE = BigInteger.TEN.pow(60)
        .add(BigInteger.valueOf(17));

    private AEBackendSmoke() {}

    public static void verify() {
        try {
            verifyPhysicalInventories();
            verifyFluidNetwork();
            for (String name : new String[] { "appeng.helpers.DualityInterface",
                "appeng.helpers.DualityInterface$InterfaceInventory", "appeng.helpers.MultiCraftingTracker",
                "appeng.parts.automation.PartImportBus", "appeng.parts.automation.PartFormationPlane",
                "appeng.tile.storage.TileIOPort" }) {
                Class.forName(name, false, AEBackendSmoke.class.getClassLoader());
            }
            AEProtocolSmoke.verify();
        } catch (ReflectiveOperationException | AppEngException error) {
            throw new IllegalStateException("AE backend verification failed", error);
        }
        Apeiron.LOG.info("AE physical inventory, fluid network and reshuffle runtime verification passed");
    }

    private static IAEItemStack diamonds(final BigInteger count) {
        return BigAEStackValues.copyWithSize(AEItemStack.create(new ItemStack(Items.diamond)), count);
    }

    private static void verifyPhysicalInventories() {
        final InventoryBasic physical = new InventoryBasic("Apeiron test", false, 3) {

            @Override
            public int getInventoryStackLimit() {
                return Integer.MAX_VALUE;
            }
        };
        final AdaptorIInventory adaptor = new AdaptorIInventory(physical, Integer.MAX_VALUE);
        final BigInteger capacity = BigInteger.valueOf(Integer.MAX_VALUE)
            .multiply(BigInteger.valueOf(3));
        final IAEItemStack input = diamonds(HUGE);
        final IAEStack<?> simulated = BigInventoryAdaptors.addStackBig(adaptor, input, InsertionMode.DEFAULT, true);
        check(
            BigAEStackValues.get(simulated)
                .equals(HUGE.subtract(capacity)),
            "physical simulation lost exact remainder");
        check(physical.getStackInSlot(0) == null, "physical simulation modified slots");
        final IAEStack<?> actual = BigInventoryAdaptors.addStackBig(adaptor, input, InsertionMode.DEFAULT, false);
        check(
            BigAEStackValues.get(actual)
                .equals(BigAEStackValues.get(simulated)),
            "physical simulation repeated capacity");
        check(
            BigAEStackValues.get(input)
                .equals(HUGE),
            "physical transfer mutated input");
        check(
            BigAEStackValues.get(BigInventoryAdaptors.extractStackBig(adaptor, input, true))
                .equals(capacity),
            "physical extraction simulation lost counts across slots");
        check(
            BigAEStackValues.get(BigInventoryAdaptors.extractStackBig(adaptor, input, false))
                .equals(capacity),
            "physical extraction did not conserve counts across slots");
        check(physical.getStackInSlot(0) == null, "physical extraction did not empty source");

        final InventoryBasic small = new InventoryBasic("Apeiron monitor test", false, 2);
        final AdaptorIInventory smallAdaptor = new AdaptorIInventory(small);
        final MEMonitorIInventory monitor = new MEMonitorIInventory(smallAdaptor);
        monitor.setActionSource(SOURCE);
        final IAEItemStack rejected = BigMEInventories.injectItemsBig(monitor, input, Actionable.SIMULATE, SOURCE);
        check(
            BigAEStackValues.get(rejected)
                .equals(HUGE.subtract(BigInteger.valueOf(128))),
            "inventory monitor simulation failed");
        check(small.getStackInSlot(0) == null, "monitor simulation mutated physical slots");
        check(
            BigAEStackValues.get(monitor.injectItems(input, Actionable.MODULATE, SOURCE))
                .equals(BigAEStackValues.get(rejected)),
            "legacy monitor injection lost exact remainder");
        final MEIInventoryWrapper wrapper = new MEIInventoryWrapper(small, smallAdaptor);
        check(
            BigAEStackValues.get(BigMEInventories.extractItemsBig(wrapper, input, Actionable.SIMULATE, SOURCE))
                .equals(BigInteger.valueOf(128)),
            "direct wrapper extraction simulation failed");
        check(small.getStackInSlot(0) != null, "direct wrapper simulation extracted real items");
        check(
            BigAEStackValues.get(wrapper.extractItems(input, Actionable.MODULATE, SOURCE))
                .equals(BigInteger.valueOf(128)),
            "legacy wrapper extraction lost items");
    }

    private static IAEFluidStack water(final BigInteger count) {
        return BigAEStackValues.copyWithSize(AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)), count);
    }

    private static void verifyFluidNetwork() throws AppEngException {
        final appeng.util.item.FluidList merged = new appeng.util.item.FluidList();
        merged.addStorage(water(HUGE));
        merged.addStorage(water(HUGE));
        check(
            BigAEStackValues.get(merged.findPrecise(water(BigInteger.ONE)))
                .equals(HUGE.multiply(BigInteger.valueOf(2))),
            "fluid list truncated an existing entry merge");
        final appeng.util.item.FluidList requests = new appeng.util.item.FluidList();
        final IAEFluidStack requestable = water(BigInteger.ZERO);
        ((com.silvia.apeiron.ae.stack.BigAERequestableStack) requestable).setCountRequestableBig(HUGE);
        requests.addRequestable(requestable);
        requests.addRequestable(requestable);
        check(
            BigAEStackValues.getCountRequestable(requests.findPrecise(water(BigInteger.ONE)))
                .equals(HUGE.multiply(BigInteger.valueOf(2))),
            "fluid list truncated requestable counts");
        final ItemStack firstItem = new ItemStack(new FluidVerificationCell());
        final FluidCellInventory first = new FluidCellInventory(firstItem, null);
        final FluidCellInventory second = new FluidCellInventory(new ItemStack(new FluidVerificationCell()), null);
        final FluidCellInventoryHandler high = new FluidCellInventoryHandler(first);
        final FluidCellInventoryHandler low = new FluidCellInventoryHandler(second);
        check(high instanceof BigCellInventoryHandler, "fluid cell handler lost exact capacity bridge");
        check(
            ((BigCellInventoryHandler) high).getStoredItemCountBig()
                .signum() == 0,
            "fluid cell handler exact count was not initialized");
        high.setPriority(10);
        low.setPriority(0);
        final NetworkInventoryHandler<IAEFluidStack> network = new NetworkInventoryHandler<>(
            AEFluidStackType.FLUID_STACK_TYPE,
            new appeng.me.cache.SecurityCache(null));
        network.addNewStorage(high);
        network.addNewStorage(low);
        final Object flow = com.silvia.apeiron.verification.FlowStatisticsFixture.create();
        final IGrid grid = (IGrid) Proxy.newProxyInstance(
            IGrid.class.getClassLoader(),
            new Class<?>[] { IGrid.class },
            (proxy, method, arguments) -> {
                if (method.getName()
                    .equals("getCache")
                    && ((Class<?>) arguments[0]).getName()
                        .equals("appeng.me.cache.ItemFlowGridCache"))
                    return flow;
                if (method.getName()
                    .equals("postEvent")) return arguments[0];
                throw new UnsupportedOperationException(method.getName());
            });
        final NetworkMonitor<IAEFluidStack> monitor = new NetworkMonitor<IAEFluidStack>(
            new GridStorageCache(grid),
            AEFluidStackType.FLUID_STACK_TYPE) {

            @Override
            public IMEInventoryHandler<IAEFluidStack> getHandler() {
                return network;
            }
        };
        final IAEFluidStack input = water(HUGE);
        check(
            monitor.injectItems(input, Actionable.SIMULATE, SOURCE) == null,
            "fluid network rejected fitting simulation");
        check(
            ((BigCellInventory) (Object) first).getStoredItemCountBig()
                .signum() == 0,
            "fluid simulation modified cell");
        check(monitor.injectItems(input, Actionable.MODULATE, SOURCE) == null, "fluid network lost big injection");
        check(
            ((BigCellInventoryHandler) high).getStoredItemCountBig()
                .equals(HUGE),
            "fluid cell handler exact count bridge lost big injection");
        final FluidCellInventory reloaded = new FluidCellInventory(firstItem, null);
        check(
            ((BigCellInventory) (Object) reloaded).getStoredItemCountBig()
                .equals(HUGE),
            "fluid NBT reload lost exact count");
        check(
            BigAEStackValues.get(network.getAvailableItem(input, IterationCounter.fetchNewId()))
                .equals(HUGE),
            "fluid availability query returned saturated count");
        final IAEFluidStack seven = water(BigInteger.valueOf(7));
        check(
            BigAEStackValues.get(monitor.extractItems(seven, Actionable.MODULATE, SOURCE))
                .equals(BigInteger.valueOf(7)),
            "small fluid extraction failed against huge balance");
        check(
            ((BigCellInventory) (Object) first).getStoredItemCountBig()
                .equals(HUGE.subtract(BigInteger.valueOf(7))),
            "small fluid extraction truncated huge remaining balance");

        verifyFluidSplitExtraction();

        final IStorageGrid storageGrid = (IStorageGrid) Proxy.newProxyInstance(
            IStorageGrid.class.getClassLoader(),
            new Class<?>[] { IStorageGrid.class },
            (proxy, method, arguments) -> {
                if (method.getName()
                    .equals("getMEMonitor")) return monitor;
                throw new UnsupportedOperationException(method.getName());
            });
        final AEStackTypeFilter filter = new AEStackTypeFilter();
        filter.setOnlyEnabled(AEFluidStackType.FLUID_STACK_TYPE);
        final IAEStackList cantInject = new IAEStackList();
        final ReshuffleTask task = reshuffle(
            filter,
            storageGrid,
            cantInject,
            new ReshuffleActionSource(null),
            false,
            true);
        task.initialize();
        for (int tick = 0; tick < 50 && task.isRunning(); tick++) task.processNextBatch();
        check(task.getReport().phase == ReshufflePhase.DONE, "reshuffle did not finish");
        check(cantInject.isEmpty(), "reshuffle unexpectedly rejected restored fluid");
        check(
            BigAEStackValues.get(network.getAvailableItem(input, IterationCounter.fetchNewId()))
                .equals(HUGE.subtract(BigInteger.valueOf(7))),
            "reshuffle did not conserve exact fluid count");

        final ReshuffleTask cancel = reshuffle(
            filter,
            storageGrid,
            cantInject,
            new ReshuffleActionSource(null),
            false,
            false);
        cancel.initialize();
        for (int tick = 0; tick < 40 && cancel.getReport().phase != ReshufflePhase.INJECTION; tick++)
            cancel.processNextBatch();
        check(cancel.getReport().phase == ReshufflePhase.INJECTION, "reshuffle cancellation test never extracted");
        cancel.cancel();
        check(
            BigAEStackValues.get(network.getAvailableItem(input, IterationCounter.fetchNewId()))
                .equals(HUGE.subtract(BigInteger.valueOf(7))),
            "reshuffle cancellation did not restore exact source count");
    }

    private static void verifyFluidSplitExtraction() throws AppEngException {
        final BigInteger half = BigInteger.valueOf(1368);
        final ItemStack firstItem = new ItemStack(new FluidVerificationCell());
        final FluidCellInventory first = new FluidCellInventory(firstItem, null);
        final FluidCellInventory second = new FluidCellInventory(new ItemStack(new FluidVerificationCell()), null);
        final FluidCellInventoryHandler high = new FluidCellInventoryHandler(first);
        final FluidCellInventoryHandler low = new FluidCellInventoryHandler(second);
        final FluidCellInventoryHandler duplicate = new FluidCellInventoryHandler(
            new FluidCellInventory(firstItem, null));
        check(
            StorageCellMounts
                .unique(
                    java.util.Arrays
                        .asList(high, new MEInventoryHandler<>(duplicate, AEFluidStackType.FLUID_STACK_TYPE), low))
                .size() == 2,
            "ordinary fluid cell duplicate mounts were not normalized");
        check(
            StorageCellMounts.unique(java.util.Arrays.asList(high, low))
                .size() == 2,
            "different ordinary fluid cells were merged");
        high.setPriority(10);
        low.setPriority(0);
        final NetworkInventoryHandler<IAEFluidStack> network = new NetworkInventoryHandler<>(
            AEFluidStackType.FLUID_STACK_TYPE,
            new appeng.me.cache.SecurityCache(null));
        network.addNewStorage(high);
        network.addNewStorage(low);
        final IAEFluidStack stored = water(half);
        check(
            BigMEInventories.injectItemsBig(first, stored, Actionable.MODULATE, SOURCE) == null,
            "first split fluid cell rejected setup");
        check(
            BigMEInventories.injectItemsBig(second, stored, Actionable.MODULATE, SOURCE) == null,
            "second split fluid cell rejected setup");
        final BigInteger total = half.multiply(BigInteger.valueOf(2L));
        final IAEFluidStack request = water(total);
        check(
            BigAEStackValues.get(network.extractItems(request, Actionable.SIMULATE, SOURCE))
                .equals(total),
            "legacy fluid network simulation lost one backend");
        check(
            BigAEStackValues.get(network.extractItems(request, Actionable.MODULATE, SOURCE))
                .equals(total),
            "legacy fluid network extraction lost one backend");
        check(
            BigAEStackValues.get(network.getAvailableItem(request, IterationCounter.fetchNewId()))
                .signum() == 0,
            "split fluid extraction left a phantom network amount");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static ReshuffleTask reshuffle(AEStackTypeFilter filter, IStorageGrid storage,
        IItemList<IAEStack<?>> rejected, ReshuffleActionSource source, boolean subnets, boolean order) {
        try {
            Class<?> sourceType = com.silvia.apeiron.compat.DependencyCapabilities.hasClass(
                "appeng.helpers.ReshuffleTask$PendingInjection") ? ReshuffleActionSource.class : BaseActionSource.class;
            return ReshuffleTask.class
                .getConstructor(
                    AEStackTypeFilter.class,
                    IStorageGrid.class,
                    IItemList.class,
                    sourceType,
                    boolean.class,
                    boolean.class)
                .newInstance(filter, storage, rejected, source, subnets, order);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot construct storage reshuffle", error);
        }
    }

    private static final class FluidVerificationCell extends ItemBasicStorageCell implements BigStorageCell {

        private FluidVerificationCell() {
            super(MaterialType.Cell1kPart, 1L);
        }

        @Override
        public IAEStackType<?> getStackType() {
            return AEFluidStackType.FLUID_STACK_TYPE;
        }

        @Override
        public BigInteger getBytesBig(final ItemStack cell) {
            return BigInteger.ONE.shiftLeft(256);
        }

        @Override
        public long getBytesLong(final ItemStack cell) {
            return Long.MAX_VALUE;
        }
    }
}
