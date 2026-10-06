package com.silvia.apeiron.common.integration.aeinfinitycell.verification;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStack;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigMEInventories;
import com.silvia.apeiron.ae.storage.BigUnlimitedMEInventory;
import com.silvia.apeiron.ae.storage.StorageCellMounts;
import com.silvia.apeiron.api.aeinfinitycell.BigCellCount;
import com.silvia.apeiron.api.aeinfinitycell.BigInfinityCellRecord;
import com.silvia.apeiron.api.machine.me.output.BigFluidOutputTransaction;
import com.silvia.apeiron.api.machine.me.output.BigItemOutputTransaction;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.me.output.verification.InfiniteMEOutputAssemblySmoke;
import com.silvia.apeiron.config.ApeironConfig;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.ICellProvider;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageMonitorable;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;
import appeng.me.cache.GridStorageCache;
import appeng.me.storage.MEInventoryHandler;
import appeng.tile.storage.TileDrive;
import appeng.tile.storage.TileIOPort;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import cn.dancingsnow.aeinfinitycell.ServerWorldAccess;
import cn.dancingsnow.aeinfinitycell.item.ItemInfinityStorageCell;
import cn.dancingsnow.aeinfinitycell.storage.CellCount;
import cn.dancingsnow.aeinfinitycell.storage.InfinityCellRecord;
import cn.dancingsnow.aeinfinitycell.storage.InfinityCellStorage;
import cpw.mods.fml.common.Loader;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.common.storage.AEEssentiaStack;

/** Opt-in checks against real transformed handlers using a temporary UUID and no world file writes. */
public final class InfinityCellSmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(60)
        .add(BigInteger.valueOf(17));

    private InfinityCellSmoke() {}

    public static void verify() {
        if (!ApeironConfig.isInfinityCellBigStorageEnabled() || !ApeironConfig.areAeMixinsEnabled()) {
            check(
                !BigInfinityCellRecord.class.isAssignableFrom(InfinityCellRecord.class),
                "disabled record integration");
            Apeiron.LOG.info("AE2 Infinity Cell big storage disabled as configured");
            return;
        }
        try {
            verifyCount();
            verifyHandlers();
            // Load both preview targets so their strict client hooks are verified during startup.
            if (Loader.isModLoaded("NotEnoughItems")) {
                Class.forName("cn.dancingsnow.aeinfinitycell.nei.InfinityCellViewHandler");
                Class.forName("cn.dancingsnow.aeinfinitycell.nei.InfinityCellViewHandler$ViewItemStack");
            }
            Apeiron.LOG.info(
                "AE2 Infinity Cell exact transfers, codecs, UUID persistence and output-cell simulation verification passed");
        } catch (final Exception failure) {
            throw new IllegalStateException("Infinity Cell verification failed", failure);
        }
    }

    private static void verifyCount() {
        final CellCount counter = new CellCount();
        counter.add(Long.MAX_VALUE);
        counter.add(1L);
        check(
            counter.toBigInteger()
                .equals(
                    BigInteger.valueOf(Long.MAX_VALUE)
                        .add(BigInteger.ONE)),
            "native overflow promotion");
        final BigCellCount exact = (BigCellCount) (Object) counter;
        exact.addAmountBig(HUGE);
        check(
            exact.extractAmountBig(HUGE)
                .equals(HUGE),
            "bulk count extraction");
        counter.extract(1L);
        check(counter.longValue() == Long.MAX_VALUE, "collapse to long");
        check(
            exact.extractAmountBig(HUGE)
                .equals(BigInteger.valueOf(Long.MAX_VALUE)) && counter.isZero(),
            "full drain");
        try {
            exact.addAmountBig(BigInteger.ONE.negate());
            throw new IllegalStateException("negative count accepted");
        } catch (final IllegalArgumentException expected) {}
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void verifyHandlers() throws Exception {
        final ItemStack cell = new ItemStack(new ItemInfinityStorageCell());
        final UUID id = ItemInfinityStorageCell.getOrCreateStorageId(cell);
        final InfinityCellStorage storage = InfinityCellStorage.getInstance();
        final Field cacheField = InfinityCellStorage.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        final Map<UUID, InfinityCellRecord> cache = (Map<UUID, InfinityCellRecord>) cacheField.get(storage);
        final InfinityCellRecord record = new InfinityCellRecord();
        cache.put(id, record);
        final Field worldField = ServerWorldAccess.class.getDeclaredField("serverWorld");
        worldField.setAccessible(true);
        final Object previousWorld = worldField.get(null);
        // Only the world's isRemote flag is read. Avoid constructing a server or touching a saved game.
        final Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        final Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        final Object unsafe = unsafeField.get(null);
        final Object temporaryWorld = unsafeClass.getMethod("allocateInstance", Class.class)
            .invoke(unsafe, WorldServer.class);
        worldField.set(null, temporaryWorld);
        try {
            final List<IAEStack<?>> types = new ArrayList<>();
            types.add(AEItemStack.create(new ItemStack(Items.diamond)));
            types.add(AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)));
            types.add(new AEEssentiaStack(Aspect.AIR, 1));
            if (Loader.isModLoaded("appeu")) types.add(
                AEStackTypeRegistry.getType("appeu.eu")
                    .getTestStack());
            final int[] changes = { 0 };
            for (final IAEStack<?> identity : types) {
                final IAEStackType type = identity.getStackType();
                final IMEInventoryHandler handler = AEApi.instance()
                    .registries()
                    .cell()
                    .getCellInventory(cell, ignored -> changes[0]++, type);
                check(handler instanceof BigUnlimitedMEInventory, "big handler " + type.getId());
                final MEInventoryHandler wrapped = new MEInventoryHandler(handler, type);
                check(BigMEInventories.hasUnlimitedCapacity(wrapped), "wrapped unlimited marker");
                final IAEStack input = BigAEStackValues.copyWithSize(identity, HUGE);
                final int before = changes[0];
                check(
                    BigMEInventories.injectItemsBig(wrapped, input, Actionable.SIMULATE, null) == null,
                    "injection simulation");
                check(
                    ((BigInfinityCellRecord) (Object) record).getAmountBig(identity)
                        .signum() == 0 && changes[0] == before,
                    "simulation wrote storage");
                check(handler.injectItems(input, Actionable.MODULATE, null) == null, "legacy injection of exact stack");
                check(
                    BigAEStackValues.get(handler.getAvailableItem(identity, 0))
                        .equals(HUGE),
                    "exact direct lookup");
                final IItemList list = handler.getAvailableItems(type.createList(), 0);
                check(
                    BigAEStackValues.get((IAEStack<?>) list.findPrecise(identity))
                        .equals(HUGE),
                    "exact inventory list");
                list.addStorage(input);
                check(
                    BigAEStackValues.get((IAEStack<?>) list.findPrecise(identity))
                        .equals(HUGE.multiply(BigInteger.valueOf(2))),
                    "list merge " + type.getId());
                verifyStack(input);
                check(
                    BigAEStackValues
                        .get(
                            BigMEInventories.extractItemsBig(
                                wrapped,
                                BigAEStackValues.copyWithSize(identity, HUGE.subtract(BigInteger.ONE)),
                                Actionable.SIMULATE,
                                null))
                        .equals(HUGE.subtract(BigInteger.ONE)),
                    "exact extraction simulation");
                check(changes[0] == before + 1, "simulation dirty notification");
                check(
                    BigAEStackValues
                        .get(
                            handler.extractItems(
                                BigAEStackValues.copyWithSize(identity, BigInteger.ONE),
                                Actionable.MODULATE,
                                null))
                        .equals(BigInteger.ONE),
                    "small legacy extraction");
                check(
                    ((BigInfinityCellRecord) (Object) record).getAmountBig(identity)
                        .equals(HUGE.subtract(BigInteger.ONE)),
                    "legacy extraction lost big remainder");
            }
            final NBTTagCompound saved = record.writeToNBT();
            record.readFromNBT(saved);
            record.readFromNBT(saved);
            for (final IAEStack<?> type : types) check(
                ((BigInfinityCellRecord) (Object) record).getAmountBig(type)
                    .equals(HUGE.subtract(BigInteger.ONE)),
                "native NBT repeated reload");
            verifyOutputCells(cell, record, types);
            final TileIOPort port = new TileIOPort();
            final Method getInv = TileIOPort.class.getDeclaredMethod("getInv", ItemStack.class);
            getInv.setAccessible(true);
            final Object first = getInv.invoke(port, cell);
            final Object second = getInv.invoke(port, cell);
            check(
                first != null && second != null
                    && ((IMEInventoryHandler) first).getStackType() != ((IMEInventoryHandler) second).getStackType(),
                "multi-channel IO rotation");
            for (final IAEStack<?> identity : types) {
                final IMEInventoryHandler handler = AEApi.instance()
                    .registries()
                    .cell()
                    .getCellInventory(cell.copy(), null, identity.getStackType());
                check(
                    BigAEStackValues.get(
                        handler.extractItems(BigAEStackValues.copyWithSize(identity, HUGE), Actionable.MODULATE, null))
                        .equals(HUGE.subtract(BigInteger.ONE)),
                    "UUID sharing and full extraction");
                check(
                    ((appeng.api.storage.ICellCacheRegistry) handler).getUsedTypes() == 0L && handler
                        .getAvailableItems(
                            identity.getStackType()
                                .createList(),
                            0)
                        .isEmpty(),
                    "drained type retained");
            }
            verifyDriveMounts(cell, types);
            verifyNetworkMounts(cell, record, types, BigInteger.valueOf(1512));
            verifyNetworkMounts(cell, record, types, HUGE);
        } finally {
            worldField.set(null, previousWorld);
            cache.remove(id);
            final Field dirtyField = InfinityCellStorage.class.getDeclaredField("dirty");
            dirtyField.setAccessible(true);
            ((Set<?>) dirtyField.get(storage)).remove(id);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void verifyDriveMounts(final ItemStack cell, final List<IAEStack<?>> types) throws Exception {
        final TileDrive drive = new TileDrive();
        final appeng.tile.inventory.AppEngInternalInventory inventory = (appeng.tile.inventory.AppEngInternalInventory) drive
            .getInternalInventory();
        final Field slots = appeng.tile.inventory.AppEngInternalInventory.class.getDeclaredField("inv");
        slots.setAccessible(true);
        ((ItemStack[]) slots.get(inventory))[0] = cell;
        final Field cached = TileDrive.class.getDeclaredField("isCached");
        cached.setAccessible(true);
        final Method update = TileDrive.class.getDeclaredMethod("updateState");
        update.setAccessible(true);
        final Field cells = TileDrive.class.getDeclaredField("cellsMap");
        cells.setAccessible(true);
        for (int rebuild = 0; rebuild < 2; rebuild++) {
            cached.setBoolean(drive, false);
            update.invoke(drive);
            update.invoke(drive);
            final Map<IAEStackType<?>, List<IMEInventoryHandler>> mounted = (Map) cells.get(drive);
            for (final IAEStack<?> identity : types) {
                final List<IMEInventoryHandler> unique = StorageCellMounts.unique(mounted.get(identity.getStackType()));
                check(
                    unique != null && unique.size() == 1,
                    "ME drive rebuild lost or duplicated a channel " + identity.getStackType()
                        .getId());
                check(
                    unique.get(0)
                        .getStackType() == identity.getStackType(),
                    "ME drive retained a wrong channel");
            }
        }
        Apeiron.LOG.info("Real ME drive channel mounting and repeated rebuild normalization verification passed");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void verifyNetworkMounts(final ItemStack cell, final InfinityCellRecord record,
        final List<IAEStack<?>> types, final BigInteger stored) {
        final BaseActionSource source = new BaseActionSource();
        final Object flow = com.silvia.apeiron.compat.OptionalFlowStatistics.verificationCache();
        final appeng.me.cache.SecurityCache security = new appeng.me.cache.SecurityCache(null);
        final IGrid grid = (IGrid) Proxy
            .newProxyInstance(IGrid.class.getClassLoader(), new Class<?>[] { IGrid.class }, (proxy, method, args) -> {
                if (method.getName()
                    .equals("getCache")
                    && ((Class<?>) args[0]).getName()
                        .equals("appeng.me.cache.ItemFlowGridCache"))
                    return flow;
                if (method.getName()
                    .equals("getCache")
                    && ((Class<?>) args[0]).getName()
                        .equals("appeng.api.networking.security.ISecurityGrid"))
                    return security;
                if (method.getName()
                    .equals("postEvent")) return args[0];
                throw new UnsupportedOperationException(method.getName());
            });
        final GridStorageCache network = new GridStorageCache(grid);
        final Map<IAEStackType<?>, List<IMEInventoryHandler>> mounted = new java.util.IdentityHashMap<>();
        for (final IAEStack<?> identity : types) {
            final IAEStackType type = identity.getStackType();
            final IMEInventoryHandler first = AEApi.instance()
                .registries()
                .cell()
                .getCellInventory(cell, null, type);
            final IMEInventoryHandler second = AEApi.instance()
                .registries()
                .cell()
                .getCellInventory(cell, null, type);
            // Reproduce two integrations mounting different wrappers over the same physical cell.
            final List<IMEInventoryHandler> duplicates = java.util.Arrays.asList(
                new MEInventoryHandler(first, type),
                new MEInventoryHandler(new MEInventoryHandler(second, type), type));
            mounted.put(type, duplicates);
            check(
                StorageCellMounts.unique(duplicates)
                    .size() == 1,
                "duplicate channel mount " + type.getId());
            check(duplicates.size() == 2, "mount normalization changed a provider's list");
            check(
                StorageCellMounts.unique(java.util.Arrays.asList(first, second))
                    .size() == 1,
                "unwrapped duplicate channel mount " + type.getId());
            final IMEInventoryHandler other = AEApi.instance()
                .registries()
                .cell()
                .getCellInventory(new ItemStack(new ItemInfinityStorageCell()), null, type);
            check(
                StorageCellMounts.unique(java.util.Arrays.asList(first, other))
                    .size() == 2,
                "distinct physical cells were merged " + type.getId());
            ((BigInfinityCellRecord) (Object) record).addStackBig(identity, stored);
        }
        final List<IMEInventoryHandler> channels = new ArrayList<>();
        for (final List<IMEInventoryHandler> entries : mounted.values()) channels.add(entries.get(0));
        check(
            StorageCellMounts.unique(channels)
                .size() == types.size(),
            "different channels of one cell were merged");
        final ICellProvider provider = new ICellProvider() {

            @Override
            public List<IMEInventoryHandler> getCellArray(final IAEStackType<?> type) {
                return mounted.getOrDefault(type, java.util.Collections.emptyList());
            }

            @Override
            public int getPriority() {
                return 0;
            }
        };
        network.registerCellProvider(provider);
        for (final IAEStack<?> identity : types) {
            final IMEMonitor monitor = network.getMEMonitor(identity.getStackType());
            check(
                BigAEStackValues.get(
                    (IAEStack) monitor.getStorageList()
                        .findPrecise(identity))
                    .equals(stored),
                "network counted the same cell twice " + identity.getStackType()
                    .getId());
            final IAEStack request = BigAEStackValues.copyWithSize(identity, stored.multiply(BigInteger.valueOf(2)));
            check(
                BigAEStackValues.get(monitor.extractItems(request, Actionable.SIMULATE, source))
                    .equals(stored),
                "network simulated duplicate storage " + identity.getStackType()
                    .getId());
            check(
                ((BigInfinityCellRecord) (Object) record).getAmountBig(identity)
                    .equals(stored),
                "duplicate simulation consumed real storage");
        }
        final MECraftingInventory crafting = new MECraftingInventory((IStorageMonitorable) network, true, false, false);
        for (final IAEStack<?> identity : types) {
            check(
                BigAEStackValues.get(crafting.getAvailableItem((IAEStack) identity))
                    .equals(stored),
                "crafting snapshot doubled a cell channel " + identity.getStackType()
                    .getId());
            check(
                BigAEStackValues.get(
                    crafting
                        .extractItems((IAEStack) BigAEStackValues.copyWithSize(identity, stored), Actionable.MODULATE))
                    .equals(stored),
                "crafting reservation lost a cell channel");
        }
        check(crafting.commit(source), "crafting commit failed against duplicate mounts");
        for (final IAEStack<?> identity : types) {
            check(
                ((BigInfinityCellRecord) (Object) record).getAmountBig(identity)
                    .signum() == 0,
                "crafting commit did not debit each channel once");
            final IMEMonitor monitor = network.getMEMonitor(identity.getStackType());
            check(
                BigAEStackValues.get(
                    (IAEStack) monitor.getStorageList()
                        .findPrecise(identity))
                    .signum() == 0,
                "network retained doubled fluid after commit");
        }
        network.unregisterCellProvider(provider);
        network.registerCellProvider(provider);
        network.unregisterCellProvider(provider);
        Apeiron.LOG.info(
            "Duplicate physical storage mounts, all-channel availability, simulation and crafting commit verification passed");
    }

    private static void verifyStack(final IAEStack<?> input) throws Exception {
        check(
            BigAEStackValues.get(input.copy())
                .equals(HUGE),
            "stack copy");
        final NBTTagCompound tag = new NBTTagCompound();
        input.writeToNBT(tag);
        check(
            BigAEStackValues.get(
                input.getStackType()
                    .loadStackFromNBT(tag))
                .equals(HUGE),
            "stack NBT");
        final ByteBuf data = Unpooled.buffer();
        try {
            input.writeToPacket(data);
            input.writeToPacket(data);
            check(
                BigAEStackValues.get(
                    input.getStackType()
                        .loadStackFromByte(data))
                    .equals(HUGE),
                "first stack packet");
            check(
                BigAEStackValues.get(
                    input.getStackType()
                        .loadStackFromByte(data))
                    .equals(HUGE) && data.readableBytes() == 0,
                "second stack packet");
        } finally {
            data.release();
        }
        ((BigAEStack) input.copy()).setStackSizeBig(BigInteger.ONE);
    }

    private static void verifyOutputCells(final ItemStack cell, final InfinityCellRecord record,
        final List<IAEStack<?>> types) {
        final MTEInfiniteMEOutputAssembly assembly = InfiniteMEOutputAssemblySmoke.assembly();
        check(assembly.isItemValidForSlot(0, cell) && assembly.isItemValidForSlot(1, cell), "assembly cell slots");
        assembly.setInventorySlotContents(0, cell.copy());
        assembly.setInventorySlotContents(1, cell.copy());
        assembly.getProvider()
            .setCacheMode(true);
        assembly.getFluidProvider()
            .setCacheMode(true);
        assembly.getProvider()
            .setCheckMode(true);
        assembly.getFluidProvider()
            .setCheckMode(true);
        check(
            assembly.getProvider()
                .isCellCacheUnlimited()
                && assembly.getFluidProvider()
                    .isCellCacheUnlimited(),
            "unlimited cache cells");
        final BigItemOutputTransaction item = assembly.createTransactionBig();
        final BigFluidOutputTransaction fluid = ((com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputHatch) assembly
            .getFluidOutput()).createTransactionBig();

        check(
            item.storePartialBig(
                (appeng.api.storage.data.IAEItemStack) BigAEStackValues.copyWithSize(types.get(0), HUGE),
                BigInteger.ONE,
                BigInteger.ONE),
            "unlimited item reservation");
        check(
            fluid.storePartialBig(
                (appeng.api.storage.data.IAEFluidStack) BigAEStackValues.copyWithSize(types.get(1), HUGE),
                BigInteger.ONE,
                BigInteger.ONE),
            "unlimited fluid reservation");
        for (int index = 0; index < 2; index++) check(
            ((BigInfinityCellRecord) (Object) record).getAmountBig(types.get(index))
                .equals(HUGE.subtract(BigInteger.ONE)),
            "copied UUID simulation modified real storage");
        check(
            assembly.getProvider()
                .getCachedAmountBig()
                .signum() == 0
                && assembly.getFluidProvider()
                    .getCachedAmountBig()
                    .signum() == 0,
            "uncommitted reservation modified cache");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new IllegalStateException("Infinity Cell: " + message);
    }
}
