// SPDX-License-Identifier: GPL-3.0-only
// Network snapshots, phantom selections and recipe-bound extraction adapted from GT Not Leisure's stocking inputs.
package com.silvia.apeiron.common.machine.me.stocking;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import com.glodblock.github.common.item.ItemFluidPacket;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.stack.InfiniteAEStack;
import com.silvia.apeiron.ae.storage.BigMEInventories;
import com.silvia.apeiron.common.machine.me.input.storage.BigPatternStackCodec;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.me.GridAccessException;
import appeng.util.IterationCounter;
import appeng.util.item.FluidList;
import appeng.util.item.ItemList;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.common.tileentities.machines.IHatchWatcher;
import gregtech.common.tileentities.machines.RecipeCheckReason;

public class StockingInputLogic {

    public static final int SLOT_COUNT = 360;

    public enum Kind {
        ITEMS,
        FLUIDS,
        MIXED
    }

    private final StockingInputHost host;
    private final Kind kind;
    private final StockingFilter filter = new StockingFilter();
    private final IAEStack<?>[] marks = new IAEStack<?>[SLOT_COUNT];
    private final IAEStack<?>[] displayed = new IAEStack<?>[SLOT_COUNT];
    private final IAEStack<?>[] stocks = new IAEStack<?>[SLOT_COUNT];
    private final Object[] views = new Object[SLOT_COUNT];
    private final int[] baselines = new int[SLOT_COUNT];
    private final List<IAEStack<?>> refunds = new ArrayList<>();
    private final Set<IHatchWatcher> watchers = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean autoPull, processing;

    public StockingInputLogic(StockingInputHost host, Kind kind) {
        this.host = host;
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    public StockingFilter getFilter() {
        return filter;
    }

    public boolean isAutoPull() {
        return autoPull;
    }

    public boolean isProcessing() {
        return processing;
    }

    public void setAutoPull(boolean value) {
        if (processing) return;
        autoPull = value;
        changed();
    }

    protected boolean active() {
        MetaTileEntity machine = (MetaTileEntity) host;
        return machine.getBaseMetaTileEntity() != null && machine.isValid()
            && host.getProxy()
                .isActive()
            && machine.getBaseMetaTileEntity()
                .isAllowedToWork();
    }

    protected BaseActionSource source() {
        return new MachineSource((appeng.api.networking.security.IActionHost) host);
    }

    @SuppressWarnings("rawtypes")
    protected IMEInventory network(IAEStack<?> stack) throws GridAccessException {
        return stack instanceof IAEFluidStack ? host.getProxy()
            .getStorage()
            .getFluidInventory()
            : host.getProxy()
                .getStorage()
                .getItemInventory();
    }

    public void changed() {
        if (host != null) ((MetaTileEntity) host).markDirty();
        if (!processing) refresh();
        for (IHatchWatcher watcher : watchers) watcher.scheduleRecipeCheck(RecipeCheckReason.IMMEDIATE);
    }

    public void addWatcher(IHatchWatcher watcher) {
        watchers.add(watcher);
    }

    public void removeWatcher(IHatchWatcher watcher) {
        watchers.remove(watcher);
    }

    public boolean setMark(int slot, IAEStack<?> value) {
        if (slot < 0 || slot >= SLOT_COUNT || processing || autoPull) return false;
        if (value != null && ((kind == Kind.ITEMS && !(value instanceof IAEItemStack))
            || (kind == Kind.FLUIDS && !(value instanceof IAEFluidStack)))) return false;
        for (int i = 0; value != null && i < SLOT_COUNT; i++) if (i != slot && same(marks[i], value)) return false;
        marks[slot] = value == null ? null : finiteCopy(value, BigInteger.ONE);
        changed();
        return true;
    }

    public static boolean same(IAEStack<?> a, IAEStack<?> b) {
        return a != null && b != null && a.getClass() == b.getClass() && a.equals(b);
    }

    public IAEStack<?> getMark(int slot) {
        return marks[slot];
    }

    public IAEStack<?> getDisplayed(int slot) {
        return displayed[slot];
    }

    public ItemStack markerItem(int slot) {
        IAEStack<?> stack = autoPull ? displayed[slot] : marks[slot];
        if (stack instanceof IAEItemStack) {
            ItemStack marker = ((IAEItemStack) stack).getItemStack();
            marker.stackSize = 1;
            return marker;
        }
        if (stack instanceof IAEFluidStack) {
            FluidStack marker = ((IAEFluidStack) stack).getFluidStack();
            marker.amount = 1;
            return ItemFluidPacket.newStack(marker);
        }
        return null;
    }

    public void tick(long tick) {
        if (processing) return;
        if (tick % filter.getRefreshTicks() == 0) {
            refundToNetwork();
            refresh();
            for (IHatchWatcher watcher : watchers) watcher.scheduleRecipeCheck(RecipeCheckReason.THROTTLED);
        }
    }

    protected List<IAEStack<?>> readNetworkStocks() throws GridAccessException {
        ItemList items = new ItemList();
        FluidList fluids = new FluidList();
        if (kind != Kind.FLUIDS) host.getProxy()
            .getStorage()
            .getItemInventory()
            .getAvailableItems(items, IterationCounter.fetchNewId());
        if (kind != Kind.ITEMS) host.getProxy()
            .getStorage()
            .getFluidInventory()
            .getAvailableItems(fluids, IterationCounter.fetchNewId());
        List<IAEStack<?>> result = new ArrayList<>();
        for (IAEItemStack item : items) result.add(item.copy());
        for (IAEFluidStack fluid : fluids) result.add(fluid.copy());
        return result;
    }

    public void refresh() {
        if (processing) return;
        Arrays.fill(displayed, null);
        if (!active()) return;
        try {
            ItemList items = new ItemList();
            FluidList fluids = new FluidList();
            for (IAEStack<?> stack : readNetworkStocks()) {
                if (stack instanceof IAEItemStack && kind != Kind.FLUIDS) items.addStorage((IAEItemStack) stack);
                if (stack instanceof IAEFluidStack && kind != Kind.ITEMS) fluids.addStorage((IAEFluidStack) stack);
            }
            if (autoPull) {
                int index = 0;
                for (IAEItemStack stack : items)
                    if (index < SLOT_COUNT && filter.accepts(stack)) displayed[index++] = stack.copy();
                for (IAEFluidStack stack : fluids)
                    if (index < SLOT_COUNT && filter.accepts(stack)) displayed[index++] = stack.copy();
            } else {
                for (int i = 0; i < SLOT_COUNT; i++) {
                    IAEStack<?> found = marks[i] instanceof IAEFluidStack ? fluids.findPrecise((IAEFluidStack) marks[i])
                        : marks[i] instanceof IAEItemStack ? items.findPrecise((IAEItemStack) marks[i]) : null;
                    if (found != null) displayed[i] = found.copy();
                }
            }
        } catch (GridAccessException ignored) {}
    }

    public void begin() {
        if (processing) return;
        refresh();
        processing = true;
        Arrays.fill(stocks, null);
        Arrays.fill(views, null);
        Arrays.fill(baselines, 0);
        if (!active()) return;
        for (int i = 0; i < SLOT_COUNT; i++) if (displayed[i] != null) {
            IAEStack<?> available = displayed[i];
            BigInteger amount = BigAEStackValues.isInfinite(available) ? BigInteger.valueOf(Integer.MAX_VALUE)
                : BigAEStackValues.get(available);
            IAEStack<?> request = finiteCopy(available, amount);
            try {
                IAEStack<?> simulated = BigMEInventories
                    .extractItemsBig(network(request), request, Actionable.SIMULATE, source());
                if (simulated == null || BigAEStackValues.get(simulated)
                    .signum() <= 0) continue;
                stocks[i] = simulated;
                if (BigAEStackValues.isInfinite(available) && simulated instanceof InfiniteAEStack)
                    ((InfiniteAEStack) simulated).setInfinite(true);
                updateView(i);
            } catch (GridAccessException ignored) {}
        }
    }

    private static IAEStack<?> finiteCopy(IAEStack<?> stack, BigInteger amount) {
        IAEStack<?> result = stack.copy();
        if (result instanceof InfiniteAEStack) ((InfiniteAEStack) result).setInfinite(false);
        BigAEStackValues.set(result, amount);
        return result;
    }

    private void updateView(int slot) {
        IAEStack<?> stock = stocks[slot];
        int amount = stock == null ? 0
            : BigAEStackValues.isInfinite(stock) ? Integer.MAX_VALUE
                : BigAEStackValues.get(stock)
                    .min(BigInteger.valueOf(Integer.MAX_VALUE))
                    .intValue();
        if (stock instanceof IAEItemStack) {
            ItemStack view = ((IAEItemStack) stock).getItemStack();
            view.stackSize = amount;
            views[slot] = view;
        } else if (stock instanceof IAEFluidStack) {
            FluidStack view = ((IAEFluidStack) stock).getFluidStack();
            view.amount = amount;
            views[slot] = view;
        } else views[slot] = null;
        baselines[slot] = amount;
    }

    public int viewIndex(Object view) {
        for (int i = 0; i < SLOT_COUNT; i++) if (view != null && views[i] == view) return i;
        return -1;
    }

    public IAEStack<?> stock(int slot) {
        return stocks[slot];
    }

    public ItemStack itemView(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return null;
        if (processing) return views[slot] instanceof ItemStack ? (ItemStack) views[slot] : null;
        IAEStack<?> stock = displayed[slot];
        if (!(stock instanceof IAEItemStack)) return null;
        ItemStack view = ((IAEItemStack) stock).getItemStack();
        view.stackSize = BigAEStackValues.isInfinite(stock) ? Integer.MAX_VALUE
            : BigAEStackValues.get(stock)
                .min(BigInteger.valueOf(Integer.MAX_VALUE))
                .intValue();
        return view;
    }

    public ItemStack[] itemViews() {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack view = itemView(i);
            if (view != null && view.stackSize > 0) result.add(view);
        }
        return result.toArray(new ItemStack[0]);
    }

    public FluidStack[] fluidViews() {
        List<FluidStack> result = new ArrayList<>();
        for (int i = 0; i < SLOT_COUNT; i++) {
            FluidStack view = processing && views[i] instanceof FluidStack ? (FluidStack) views[i]
                : !processing && displayed[i] instanceof IAEFluidStack ? ((IAEFluidStack) displayed[i]).getFluidStack()
                    : null;
            if (view != null) {
                if (!processing) view.amount = BigAEStackValues.isInfinite(displayed[i]) ? Integer.MAX_VALUE
                    : BigAEStackValues.get(displayed[i])
                        .min(BigInteger.valueOf(Integer.MAX_VALUE))
                        .intValue();
                if (view.amount > 0) result.add(view);
            }
        }
        return result.toArray(new FluidStack[0]);
    }

    public void recordCommitted(int slot, BigInteger debit) {
        if (!BigAEStackValues.isInfinite(stocks[slot])) BigAEStackValues.set(
            stocks[slot],
            BigAEStackValues.get(stocks[slot])
                .subtract(debit));
        // Keep the identity exported to the controller, so later recipe candidates cannot see a stale object.
        int remaining = BigAEStackValues.isInfinite(stocks[slot]) ? Integer.MAX_VALUE
            : BigAEStackValues.get(stocks[slot])
                .min(BigInteger.valueOf(Integer.MAX_VALUE))
                .intValueExact();
        if (views[slot] instanceof ItemStack) ((ItemStack) views[slot]).stackSize = remaining;
        if (views[slot] instanceof FluidStack) ((FluidStack) views[slot]).amount = remaining;
        baselines[slot] = remaining;
    }

    public CheckRecipeResult end() {
        return finishGroup(Collections.singletonList(this));
    }

    private List<IAEStack<?>> legacyDebits() {
        List<IAEStack<?>> debits = new ArrayList<>();
        for (int i = 0; i < SLOT_COUNT; i++) if (stocks[i] != null) {
            int remaining = views[i] instanceof ItemStack ? ((ItemStack) views[i]).stackSize
                : ((FluidStack) views[i]).amount;
            if (remaining < 0 || remaining > baselines[i]) return null;
            if (remaining < baselines[i])
                debits.add(finiteCopy(stocks[i], BigInteger.valueOf(baselines[i] - remaining)));
        }
        return debits;
    }

    public static CheckRecipeResult finishGroup(java.util.Collection<StockingInputLogic> group) {
        Map<StockingInputLogic, List<IAEStack<?>>> debits = new LinkedHashMap<>();
        boolean valid = true;
        for (StockingInputLogic logic : group) if (logic.processing) {
            List<IAEStack<?>> pending = logic.legacyDebits();
            if (pending == null) valid = false;
            else debits.put(logic, pending);
        }
        boolean success = valid && commit(debits);
        for (StockingInputLogic logic : group) if (logic.processing) {
            logic.processing = false;
            logic.refresh();
        }
        return success ? CheckRecipeResultRegistry.SUCCESSFUL : failed();
    }

    private static CheckRecipeResult failed() {
        return SimpleCheckRecipeResult.ofFailurePersistOnShutdown("stocking_bus_fail_extraction");
    }

    /** All requests are checked first. Partial extraction is returned; a rejected refund is persisted locally. */
    public static boolean commit(Map<StockingInputLogic, List<IAEStack<?>>> debits) {
        Map<StockingInputLogic, List<IAEStack<?>>> taken = new LinkedHashMap<>();
        try {
            for (Map.Entry<StockingInputLogic, List<IAEStack<?>>> entry : debits.entrySet())
                for (IAEStack<?> request : entry.getValue()) {
                    StockingInputLogic owner = entry.getKey();
                    IAEStack<?> result = owner.active()
                        ? BigMEInventories
                            .extractItemsBig(owner.network(request), request, Actionable.SIMULATE, owner.source())
                        : null;
                    if (result == null || !BigAEStackValues.get(result)
                        .equals(BigAEStackValues.get(request))) return false;
                }
            for (Map.Entry<StockingInputLogic, List<IAEStack<?>>> entry : debits.entrySet())
                for (IAEStack<?> request : entry.getValue()) {
                    StockingInputLogic owner = entry.getKey();
                    IAEStack<?> result = BigMEInventories
                        .extractItemsBig(owner.network(request), request, Actionable.MODULATE, owner.source());
                    if (result != null && BigAEStackValues.get(result)
                        .signum() > 0) taken.computeIfAbsent(owner, key -> new ArrayList<>())
                            .add(result);
                    if (result == null || !BigAEStackValues.get(result)
                        .equals(BigAEStackValues.get(request))) {
                        rollback(taken);
                        return false;
                    }
                }
            return true;
        } catch (GridAccessException | RuntimeException failure) {
            rollback(taken);
            return false;
        }
    }

    private static void rollback(Map<StockingInputLogic, List<IAEStack<?>>> taken) {
        for (Map.Entry<StockingInputLogic, List<IAEStack<?>>> entry : taken.entrySet())
            for (IAEStack<?> stack : entry.getValue()) {
                StockingInputLogic owner = entry.getKey();
                try {
                    IAEStack<?> remainder = BigMEInventories
                        .injectItemsBig(owner.network(stack), stack, Actionable.MODULATE, owner.source());
                    if (remainder != null) owner.addRefund(remainder);
                } catch (GridAccessException | RuntimeException failure) {
                    owner.addRefund(stack);
                }
            }
    }

    private void addRefund(IAEStack<?> stack) {
        for (IAEStack<?> refund : refunds) if (same(refund, stack)) {
            BigAEStackValues.addStorage(refund, stack);
            changed();
            return;
        }
        refunds.add(stack.copy());
        changed();
    }

    private void refundToNetwork() {
        if (!active()) return;
        for (int i = refunds.size() - 1; i >= 0; i--) {
            IAEStack<?> stack = refunds.get(i);
            try {
                IAEStack<?> remainder = BigMEInventories
                    .injectItemsBig(network(stack), stack, Actionable.MODULATE, source());
                if (remainder == null) refunds.remove(i);
                else refunds.set(i, remainder);
            } catch (GridAccessException | RuntimeException ignored) {}
        }
        if (host != null) ((MetaTileEntity) host).markDirty();
    }

    public BigInteger getRefundAmount(boolean fluid) {
        BigInteger total = BigInteger.ZERO;
        for (IAEStack<?> stack : refunds)
            if ((stack instanceof IAEFluidStack) == fluid) total = total.add(BigAEStackValues.get(stack));
        return total;
    }

    public NBTTagCompound snapshot() {
        return snapshot(0, SLOT_COUNT);
    }

    public NBTTagCompound snapshot(int start, int end) {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        int first = Math.max(0, start);
        int last = Math.min(SLOT_COUNT, end);
        for (int i = first; i < last; i++) {
            IAEStack<?> marker = autoPull ? displayed[i] : marks[i];
            if (marker == null && displayed[i] == null) continue;
            NBTTagCompound cell = new NBTTagCompound();
            cell.setInteger("slot", i);
            if (marker != null) cell.setTag("mark", BigPatternStackCodec.write(marker));
            if (displayed[i] != null) cell.setTag("available", BigPatternStackCodec.write(displayed[i]));
            cell.setBoolean("infinite", BigAEStackValues.isInfinite(displayed[i]));
            list.appendTag(cell);
        }
        tag.setTag("slots", list);
        return tag;
    }

    public void save(NBTTagCompound root) {
        NBTTagCompound tag = filter.save();
        tag.setBoolean("auto", autoPull);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < SLOT_COUNT; i++) if (marks[i] != null) {
            NBTTagCompound cell = BigPatternStackCodec.write(marks[i]);
            cell.setInteger("slot", i);
            list.appendTag(cell);
        }
        tag.setTag("marks", list);
        NBTTagList recovery = new NBTTagList();
        for (IAEStack<?> stack : refunds) recovery.appendTag(BigPatternStackCodec.write(stack));
        tag.setTag("refunds", recovery);
        root.setTag("ApeironStockingInput", tag);
    }

    public NBTTagCompound copyConfiguration() {
        NBTTagCompound root = new NBTTagCompound();
        save(root);
        root.getCompoundTag("ApeironStockingInput")
            .removeTag("refunds");
        root.setString("type", "apeiron.stocking." + kind.name());
        return root;
    }

    public boolean pasteConfiguration(NBTTagCompound root) {
        if (processing || root == null
            || !root.getString("type")
                .equals("apeiron.stocking." + kind.name()))
            return false;
        List<IAEStack<?>> retained = new ArrayList<>(refunds);
        load(root);
        refunds.addAll(retained);
        changed();
        return true;
    }

    public void load(NBTTagCompound root) {
        NBTTagCompound tag = root.getCompoundTag("ApeironStockingInput");
        filter.load(tag);
        autoPull = tag.getBoolean("auto");
        Arrays.fill(marks, null);
        NBTTagList list = tag.getTagList("marks", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound cell = list.getCompoundTagAt(i);
            int slot = cell.getInteger("slot");
            IAEStack<?> mark = BigPatternStackCodec.read(cell);
            if (slot < 0 || slot >= SLOT_COUNT
                || mark == null
                || kind == Kind.ITEMS && !(mark instanceof IAEItemStack)
                || kind == Kind.FLUIDS && !(mark instanceof IAEFluidStack)) continue;
            boolean duplicate = false;
            for (IAEStack<?> current : marks) duplicate |= same(current, mark);
            if (!duplicate) marks[slot] = finiteCopy(mark, BigInteger.ONE);
        }
        refunds.clear();
        list = tag.getTagList("refunds", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            IAEStack<?> stack = BigPatternStackCodec.read(list.getCompoundTagAt(i));
            if (stack != null) refunds.add(stack);
        }
        processing = false;
    }
}
