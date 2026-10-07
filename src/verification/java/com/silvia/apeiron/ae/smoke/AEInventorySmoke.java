package com.silvia.apeiron.ae.smoke;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEItemStacks;
import com.silvia.apeiron.ae.storage.BigCellInventory;
import com.silvia.apeiron.ae.storage.BigIMEInventory;
import com.silvia.apeiron.ae.storage.BigMEInventories;
import com.silvia.apeiron.ae.storage.BigStorageCell;

import appeng.api.config.Actionable;
import appeng.api.exceptions.AppEngException;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEItemStack;
import appeng.items.materials.MaterialType;
import appeng.items.storage.ItemBasicStorageCell;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.NetworkMonitor;
import appeng.me.storage.ItemCellInventory;
import appeng.me.storage.ItemCellInventoryHandler;
import appeng.me.storage.NetworkInventoryHandler;
import appeng.util.IterationCounter;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;

/** Optional checks using the actual transformed cells and network classes, enabled with the stack smoke check. */
public final class AEInventorySmoke {

    private static final BaseActionSource SOURCE = new BaseActionSource();

    private AEInventorySmoke() {}

    public static void verify() {
        try {
            verifyCells();
            verifyNetwork();
        } catch (AppEngException error) {
            throw new IllegalStateException("AE big inventory verification failed", error);
        }
        Apeiron.LOG.info("AE item input/output big-count runtime verification passed");
    }

    private static IAEItemStack diamonds(BigInteger count) {
        return BigAEItemStacks.copyWithSize(AEItemStack.create(new ItemStack(Items.diamond)), count);
    }

    private static BigInteger count(IAEItemStack stack) {
        return stack == null ? BigInteger.ZERO : BigAEItemStacks.stackSize(stack);
    }

    private static void verifyCells() throws AppEngException {
        BigInteger huge = BigInteger.ONE.shiftLeft(128)
            .add(BigInteger.valueOf(37L));
        ItemStack cellItem = new ItemStack(new VerificationCell(BigInteger.ONE.shiftLeft(256)));
        ItemCellInventory cell = new ItemCellInventory(cellItem, null);
        BigCellInventory bigCell = (BigCellInventory) (Object) cell;
        IAEItemStack offered = diamonds(huge);
        NBTTagCompound beforeSimulation = (NBTTagCompound) cellItem.getTagCompound()
            .copy();
        check(
            BigMEInventories.injectItemsBig(cell, offered, Actionable.SIMULATE, SOURCE) == null,
            "big-capacity cell rejected a fitting simulation");
        check(
            bigCell.getStoredItemCountBig()
                .signum() == 0,
            "simulation mutated big cell");
        check(beforeSimulation.equals(cellItem.getTagCompound()), "simulation changed cell NBT");
        check(
            BigMEInventories.injectItemsBig(cell, offered, Actionable.MODULATE, SOURCE) == null,
            "big-capacity cell rejected a fitting injection");
        check(
            bigCell.getStoredItemCountBig()
                .equals(huge),
            "big cell total was truncated");
        check(cell.getStoredItemCount() == Long.MAX_VALUE, "legacy total was not saturated");
        check(count(offered).equals(huge), "injection changed the caller's quantity");

        ItemCellInventory reloaded = new ItemCellInventory(cellItem, null);
        BigCellInventory exactReloaded = (BigCellInventory) (Object) reloaded;
        check(
            exactReloaded.getStoredItemCountBig()
                .equals(huge),
            "big cell total was lost on reload");
        check(
            count(reloaded.getAvailableItem(offered, IterationCounter.fetchNewId())).equals(huge),
            "cell item query lost exact quantity");
        IAEItemStack partial = diamonds(huge.subtract(BigInteger.valueOf(23L)));
        check(
            count(BigMEInventories.extractItemsBig(reloaded, partial, Actionable.SIMULATE, SOURCE))
                .equals(count(partial)),
            "big extraction simulation returned the wrong amount");
        check(
            exactReloaded.getStoredItemCountBig()
                .equals(huge),
            "extraction simulation mutated big cell");
        check(
            count(BigMEInventories.extractItemsBig(reloaded, partial, Actionable.MODULATE, SOURCE))
                .equals(count(partial)),
            "big extraction was truncated");
        check(
            exactReloaded.getStoredItemCountBig()
                .equals(BigInteger.valueOf(23L)),
            "big count did not fall back to long");
        check(
            !cellItem.getTagCompound()
                .hasKey("Apeironic"),
            "small total retained stale big NBT");
        check(
            count(reloaded.extractItems(diamonds(BigInteger.valueOf(3L)), Actionable.MODULATE, SOURCE))
                .equals(BigInteger.valueOf(3L)),
            "legacy extraction from a big-capacity cell failed");
        check(
            exactReloaded.getStoredItemCountBig()
                .equals(BigInteger.valueOf(20L)),
            "legacy extraction lost the remaining count");

        // A long byte capacity can hold more than long items; its multiplication must not wrap.
        ItemStack wideItem = new ItemStack(new VerificationCell(BigInteger.valueOf(Long.MAX_VALUE)));
        ItemCellInventory wide = new ItemCellInventory(wideItem, null);
        BigCellInventory exactWide = (BigCellInventory) (Object) wide;
        IAEItemStack request = diamonds(BigInteger.ONE.shiftLeft(67));
        BigInteger simulated = count(BigMEInventories.injectItemsBig(wide, request, Actionable.SIMULATE, SOURCE));
        BigInteger remainder = count(BigMEInventories.injectItemsBig(wide, request, Actionable.MODULATE, SOURCE));
        check(simulated.equals(remainder), "partial big injection disagreed with simulation");
        check(
            exactWide.getStoredItemCountBig()
                .compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0,
            "wide byte capacity did not store more than long items");
        check(
            exactWide.getStoredItemCountBig()
                .add(remainder)
                .equals(count(request)),
            "partial big injection did not conserve items");
        check(
            exactWide.getUsedBytesBig()
                .compareTo(exactWide.getTotalBytesBig()) <= 0,
            "partial injection exceeded real byte capacity");
        check(
            count(BigMEInventories.extractItemsBig(wide, request, Actionable.MODULATE, SOURCE)).add(remainder)
                .equals(count(request)),
            "extraction from wide cell did not conserve items");
    }

    private static void verifyNetwork() throws AppEngException {
        BigInteger unit = BigInteger.ONE.shiftLeft(100);
        ItemCellInventory highCell = new ItemCellInventory(
            new ItemStack(
                new VerificationCell(
                    unit.divide(BigInteger.valueOf(8L))
                        .add(BigInteger.valueOf(8L)))),
            null);
        ItemCellInventory lowCell = new ItemCellInventory(
            new ItemStack(new VerificationCell(BigInteger.ONE.shiftLeft(256))),
            null);
        ItemCellInventoryHandler high = new ItemCellInventoryHandler(highCell);
        ItemCellInventoryHandler low = new ItemCellInventoryHandler(lowCell);
        high.setPriority(10);
        low.setPriority(0);
        NetworkInventoryHandler<IAEItemStack> network = new NetworkInventoryHandler<>(
            AEItemStackType.ITEM_STACK_TYPE,
            null);
        network.addNewStorage(high);
        network.addNewStorage(low);
        check(network instanceof BigIMEInventory, "network big inventory mixin was not applied");

        Object flow = com.silvia.apeiron.verification.FlowStatisticsFixture.create();
        IGrid grid = (IGrid) Proxy
            .newProxyInstance(IGrid.class.getClassLoader(), new Class<?>[] { IGrid.class }, (proxy, method, args) -> {
                if (method.getName()
                    .equals("getCache")
                    && ((Class<?>) args[0]).getName()
                        .equals("appeng.me.cache.ItemFlowGridCache"))
                    return flow;
                if (method.getName()
                    .equals("postEvent")) return args[0];
                throw new UnsupportedOperationException("unexpected smoke grid call: " + method.getName());
            });
        GridStorageCache cache = new GridStorageCache(grid);
        NetworkMonitor<IAEItemStack> monitor = new NetworkMonitor<IAEItemStack>(
            cache,
            AEItemStackType.ITEM_STACK_TYPE) {

            @Override
            public IMEInventoryHandler<IAEItemStack> getHandler() {
                return network;
            }
        };
        check(monitor instanceof BigIMEInventory, "network monitor big inventory mixin was not applied");
        List<BigInteger> changes = new ArrayList<>();
        monitor.addListener(new IMEMonitorHandlerReceiver<IAEItemStack>() {

            @Override
            public boolean isValid(Object token) {
                return true;
            }

            @Override
            public void postChange(IBaseMonitor<IAEItemStack> observed, Iterable<IAEItemStack> change,
                BaseActionSource source) {
                for (IAEItemStack stack : change) changes.add(count(stack));
            }

            @Override
            public void onListUpdate() {}
        }, new Object());

        IAEItemStack input = diamonds(unit.multiply(BigInteger.valueOf(3L)));
        check(
            BigMEInventories.injectItemsBig(monitor, input, Actionable.SIMULATE, SOURCE) == null,
            "network simulation rejected fitting input");
        check(changes.isEmpty(), "network simulation notified listeners");
        check(
            ((BigCellInventory) (Object) highCell).getStoredItemCountBig()
                .signum() == 0,
            "network simulation mutated the first cell");
        check(
            BigMEInventories.injectItemsBig(monitor, input, Actionable.MODULATE, SOURCE) == null,
            "network injection did not use multiple backends");
        check(
            ((BigCellInventory) (Object) highCell).getStoredItemCountBig()
                .equals(unit),
            "network did not honor storage priority");
        check(
            ((BigCellInventory) (Object) lowCell).getStoredItemCountBig()
                .equals(unit.shiftLeft(1)),
            "network lost the exact remainder between backends");
        check(
            changes.size() == 1 && changes.get(0)
                .equals(count(input)),
            "insertion change delta was truncated");
        check(
            count(BigMEInventories.getAvailableItemBig(network, input, IterationCounter.fetchNewId()))
                .equals(count(input)),
            "network query lost the exact aggregate count");
        check(
            count(network.getAvailableItem(input, IterationCounter.fetchNewId())).equals(count(input)),
            "legacy network query lost the exact aggregate count");
        check(
            count(BigMEInventories.extractItemsBig(monitor, input, Actionable.SIMULATE, SOURCE)).equals(count(input)),
            "network simulation did not aggregate big extraction");
        check(changes.size() == 1, "extraction simulation notified listeners");
        check(
            count(monitor.extractItems(input, Actionable.MODULATE, SOURCE)).equals(count(input)),
            "legacy network extraction lost big quantity");
        check(
            changes.size() == 2 && changes.get(1)
                .equals(count(input).negate()),
            "extraction change delta was truncated");
        check(
            monitor.injectItems(input, Actionable.MODULATE, SOURCE) == null,
            "legacy network injection lost big quantity");
        monitor.setLocked(true);
        check(
            BigMEInventories.injectItemsBig(monitor, input, Actionable.MODULATE, SOURCE) == input,
            "locked network accepted injection");
        check(
            BigMEInventories.extractItemsBig(monitor, input, Actionable.MODULATE, SOURCE) == null,
            "locked network allowed extraction");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    /** An unregistered in-memory test cell; it adds no item to the game. */
    private static final class VerificationCell extends ItemBasicStorageCell implements BigStorageCell {

        private final BigInteger bytes;

        private VerificationCell(BigInteger bytes) {
            super(MaterialType.Cell1kPart, 1L);
            this.bytes = bytes;
        }

        @Override
        public BigInteger getBytesBig(ItemStack cell) {
            return bytes;
        }

        @Override
        public long getBytesLong(ItemStack cell) {
            return bytes.bitLength() <= 63 ? bytes.longValue() : Long.MAX_VALUE;
        }
    }
}
