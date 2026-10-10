package com.silvia.apeiron.ae.smoke;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.crafting.core.BigCraftingCPU;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.crafting.BigCraftingJobFast;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.CraftingMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IMachineSet;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.crafting.MECraftingInventory;
import appeng.helpers.UltimatePatternHelper;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.tile.crafting.TileCraftingTile;
import appeng.util.inv.MEInventoryCrafting;
import appeng.util.item.AEItemStack;

/** Runs real CPU submission, execution, output reservation, link completion and inventory return. */
@SuppressWarnings({ "rawtypes", "unchecked" })
public final class AESelfRecursiveCraftingSmoke {

    private AESelfRecursiveCraftingSmoke() {}

    public static void verify() {
        for (boolean automatic : new boolean[] { false, true }) {
            for (boolean split : new boolean[] { false, true }) {
                run(1, 2, 10, automatic, false, split);
                run(2, 5, 10, automatic, true, split);
            }
        }
        verifyMerge();
        verifyReload();
        verifyExactBalance();
        verifyOversizedReturn();
        Apeiron.LOG.info(
            "AE self-recursive CPU execution, split/batch outputs, requester completion, merges, reload, exact balances and inventory return verification passed");
    }

    private static void run(int seed, int produced, int requested, boolean automatic, boolean byproduct,
        boolean split) {
        Fixture fixture = new Fixture(seed, produced, byproduct);
        fixture.split = split;
        ICraftingRequester requester = automatic ? fixture.requester(requested) : null;
        ICraftingLink link = fixture.submit(requested, requester);
        int rounds = (requested + produced - seed - 1) / (produced - seed);
        fixture.execute(rounds);
        fixture.tick();
        check(completed(fixture.cpu), "self-recursive CPU did not complete after all outputs arrived");
        check(!fixture.cpu.isBusy(), "completed self-recursive CPU remained busy");
        check(outputs(fixture.cpu).isEmpty(), "completed CPU retained a final-output balance");
        check(fixture.cpu.getUsedStorage() == 0, "completed CPU retained used storage");
        if (automatic) {
            check(link.isDone() && fixture.notifications == 1, "requester never received job completion");
            check(fixture.delivered == requested, "requester received the wrong number of items");
        }
        check(
            fixture.count(Items.diamond) == seed + rounds * (produced - seed) - fixture.delivered,
            "seed or rounding surplus was lost instead of returning to ME: stored=" + fixture.count(Items.diamond)
                + " inventory="
                + fixture.cpu.getInventory()
                    .findPrecise(stack(Items.diamond, 1)));
        if (byproduct) check(fixture.count(Items.emerald) == rounds, "byproducts were not returned to ME");

        // A leftover unrelated item proves native storeItems actually runs after completion.
        fixture.cpu.addStorage(stack(Items.gold_ingot, 3));
        fixture.rejectStorage = true;
        fixture.tick();
        check(
            !fixture.cpu.getInventory()
                .isEmpty(),
            "full network caused leftover items to be discarded");
        check(fixture.count(Items.gold_ingot) == 0, "full network unexpectedly accepted leftovers");
        fixture.rejectStorage = false;
        fixture.tick();
        check(
            fixture.cpu.getInventory()
                .isEmpty(),
            "completed CPU did not return leftovers when space became available");
        check(fixture.count(Items.gold_ingot) == 3, "leftover return lost or duplicated items");
        check(fixture.submit(1, null) != null, "emptied CPU could not accept the next job");
        fixture.execute(1);
        fixture.tick();
        check(
            completed(fixture.cpu) && fixture.cpu.getInventory()
                .isEmpty(),
            "second job failed to release CPU");
    }

    private static void verifyMerge() {
        Fixture fixture = new Fixture(1, 2, true);
        fixture.network.injectItems(stack(Items.diamond, 1), Actionable.MODULATE);
        fixture.submit(4, null);
        fixture.submit(7, null);
        fixture.execute(11);
        fixture.tick();
        check(completed(fixture.cpu), "merged self-recursive requests failed to complete");
        check(
            fixture.cpu.getInventory()
                .isEmpty(),
            "merged requests retained CPU inventory");
        check(fixture.count(Items.diamond) == 13, "merged requests lost their two seeds or net outputs");
        check(fixture.count(Items.emerald) == 11, "merged requests lost byproducts");
    }

    private static void verifyReload() {
        Fixture fixture = new Fixture(2, 5, true);
        ICraftingLink link = fixture.submit(10, fixture.requester(10));
        fixture.execute(1);
        NBTTagCompound saved = new NBTTagCompound();
        fixture.cpu.writeToNBT(saved);
        fixture.cpu = newCPU(fixture.grid);
        fixture.cpu.readFromNBT(saved);
        fixture.execute(3);
        fixture.tick();
        check(completed(fixture.cpu) && link.isDone(), "reloaded recursive CPU or requester did not complete");
        check(
            fixture.delivered == 10 && fixture.notifications == 1,
            "reloaded request delivered or notified incorrectly");
        check(fixture.count(Items.diamond) == 4 && fixture.count(Items.emerald) == 4, "reload lost seeds or surplus");
        check(
            fixture.cpu.getInventory()
                .isEmpty(),
            "reloaded recursive CPU retained inventory");
    }

    private static void verifyExactBalance() {
        Fixture fixture = new Fixture(2, 5, true);
        BigInteger requested = BigInteger.TEN.pow(60);
        BigInteger rounds = requested.add(BigInteger.valueOf(2))
            .divide(BigInteger.valueOf(3));
        ((BigCraftingCPU) (Object) fixture.cpu).addCraftingBig(fixture.pattern, rounds);
        IAEItemStack output = BigAEStackValues.copyWithSize(stack(Items.diamond, 1), requested);
        outputs(fixture.cpu).init(output);
        check(
            balance(fixture.cpu, Items.diamond).equals(rounds.multiply(BigInteger.valueOf(3))),
            "exact recursive completion balance used gross output or truncated count");
        check(balance(fixture.cpu, Items.emerald).equals(rounds), "exact byproduct completion balance was truncated");
        reloadOutput(fixture.cpu);
        check(
            balance(fixture.cpu, Items.diamond).equals(rounds.multiply(BigInteger.valueOf(3))),
            "saved final-output balance lost its exact quantity");
    }

    private static void verifyOversizedReturn() {
        Fixture fixture = new Fixture(2, 5, true);
        fixture.split = false;
        fixture.extra = 1;
        ICraftingLink link = fixture.submit(10, fixture.requester(10));
        fixture.execute(4);
        fixture.tick();
        check(completed(fixture.cpu) && link.isDone(), "oversized return prevented job completion");
        check(
            fixture.delivered == 10 && fixture.count(Items.diamond) == 8,
            "oversized return lost seed or excess items");
        check(
            fixture.cpu.getInventory()
                .isEmpty(),
            "oversized return left CPU inventory stranded");
    }

    private static IAEItemStack stack(Item item, long amount) {
        IAEItemStack result = AEItemStack.create(new ItemStack(item));
        result.setStackSize(amount);
        return result;
    }

    private static ICraftingPatternDetails pattern(int seed, int produced, boolean byproduct) {
        ItemStack encoded = AEApi.instance()
            .definitions()
            .items()
            .encodedUltimatePattern()
            .maybeStack(1)
            .get();
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList in = new NBTTagList();
        for (IAEItemStack stack : new IAEItemStack[] { stack(Items.diamond, seed), stack(Items.stick, 1) }) {
            NBTTagCompound value = new NBTTagCompound();
            stack.writeToNBT(value);
            in.appendTag(value);
        }
        NBTTagList out = new NBTTagList();
        NBTTagCompound value = new NBTTagCompound();
        stack(Items.diamond, produced).writeToNBT(value);
        out.appendTag(value);
        if (byproduct) {
            value = new NBTTagCompound();
            stack(Items.emerald, 1).writeToNBT(value);
            out.appendTag(value);
        }
        tag.setTag("in", in);
        tag.setTag("out", out);
        encoded.setTagCompound(tag);
        return new UltimatePatternHelper(encoded);
    }

    private static final class Fixture {

        final MECraftingInventory network = new MECraftingInventory();
        final Queue<IAEStack<?>> returned = new ArrayDeque<>();
        final ICraftingPatternDetails pattern;
        final IGrid grid;
        CraftingGridCache crafting;
        final IEnergyGrid energy;
        CraftingCPUCluster cpu;
        boolean split = true;
        int extra;
        boolean rejectStorage;
        int delivered;
        int notifications;
        int executions;

        Fixture(int seed, int produced, boolean byproduct) {
            pattern = pattern(seed, produced, byproduct);
            network.injectItems(stack(Items.diamond, seed), Actionable.MODULATE);
            network.injectItems(stack(Items.stick, 100), Actionable.MODULATE);
            IStorageGrid storage = (IStorageGrid) Proxy.newProxyInstance(
                IStorageGrid.class.getClassLoader(),
                new Class<?>[] { IStorageGrid.class },
                (proxy, method, args) -> {
                    if (method.getName()
                        .equals("getMEMonitor")) {
                        IAEStackType<?> type = (IAEStackType<?>) args[0];
                        return Proxy.newProxyInstance(
                            IMEMonitor.class.getClassLoader(),
                            new Class<?>[] { IMEMonitor.class },
                            (monitor, call, values) -> {
                                if (call.getName()
                                    .equals("getStorageList")) return network.getAvailableItems(type.createList());
                                if (call.getName()
                                    .equals("extractItems"))
                                    return network.extractItems((IAEStack) values[0], (Actionable) values[1]);
                                if (call.getName()
                                    .equals("injectItems")) {
                                    if (rejectStorage) return ((IAEStack) values[0]).copy();
                                    network.injectItems((IAEStack) values[0], (Actionable) values[1]);
                                    return null;
                                }
                                return null;
                            });
                    }
                    return null;
                });
            grid = (IGrid) Proxy.newProxyInstance(
                IGrid.class.getClassLoader(),
                new Class<?>[] { IGrid.class },
                (proxy, method, args) -> {
                    if (method.getName()
                        .equals("getCache"))
                        return args[0] == ICraftingGrid.class ? crafting
                            : args[0] == IStorageGrid.class ? storage : null;
                    if (method.getName()
                        .equals("getMachines")) {
                        return Proxy.newProxyInstance(
                            IMachineSet.class.getClassLoader(),
                            new Class<?>[] { IMachineSet.class },
                            (machines, call, values) -> {
                                if (call.getName()
                                    .equals("getMachineClass")) return args[0];
                                if (call.getName()
                                    .equals("isEmpty")) return true;
                                if (call.getName()
                                    .equals("size")) return 0;
                                if (call.getName()
                                    .equals("iterator"))
                                    return Collections.emptyList()
                                        .iterator();
                                return null;
                            });
                    }
                    return null;
                });
            ICraftingMedium medium = (ICraftingMedium) Proxy.newProxyInstance(
                ICraftingMedium.class.getClassLoader(),
                new Class<?>[] { ICraftingMedium.class },
                (proxy, method, args) -> {
                    if (method.getName()
                        .equals("isBusy")) return false;
                    if (method.getName()
                        .equals("getBlockingMode")) return ICraftingMedium.BlockingMode.NONE;
                    if (method.getName()
                        .equals("pushPattern")) {
                        MEInventoryCrafting inputs = (MEInventoryCrafting) args[1];
                        check(
                            inputs.getAEStackInSlot(0)
                                .getStackSize() == seed,
                            "CPU dispatched the wrong seed amount");
                        check(
                            inputs.getAEStackInSlot(1)
                                .getStackSize() == 1,
                            "CPU dispatched the wrong external input amount");
                        executions++;
                        // Exercise both output orders and both receiver branches (including more than expected).
                        if (byproduct && split) returned.add(stack(Items.emerald, 1));
                        if (split) {
                            for (int i = 0; i < produced; i++) returned.add(stack(Items.diamond, 1));
                        } else {
                            returned.add(stack(Items.diamond, produced + extra));
                        }
                        if (byproduct && !split) returned.add(stack(Items.emerald, 1));
                        return true;
                    }
                    return null;
                });
            crafting = new CraftingGridCache(grid) {

                @Override
                public ImmutableMap<IAEStack<?>, ImmutableList<ICraftingPatternDetails>> getCraftingMultiPatterns() {
                    return ImmutableMap.of(stack(Items.diamond, 1), ImmutableList.of(pattern));
                }

                @Override
                public List<ICraftingMedium> getMediums(ICraftingPatternDetails details) {
                    return Collections.singletonList(medium);
                }
            };
            energy = (IEnergyGrid) Proxy.newProxyInstance(
                IEnergyGrid.class.getClassLoader(),
                new Class<?>[] { IEnergyGrid.class },
                (proxy, method, args) -> method.getName()
                    .equals("extractAEPower") ? args[0] : null);
            cpu = newCPU(grid);
        }

        ICraftingRequester requester(int amount) {
            return (ICraftingRequester) Proxy.newProxyInstance(
                ICraftingRequester.class.getClassLoader(),
                new Class<?>[] { ICraftingRequester.class },
                (proxy, method, args) -> {
                    if (method.getName()
                        .equals("injectCraftedItems")) {
                        IAEStack<?> incoming = (IAEStack<?>) args[1];
                        long accepted = incoming.equals(stack(Items.diamond, 1))
                            ? Math.min(incoming.getStackSize(), amount - delivered)
                            : 0;
                        if (args[2] == Actionable.MODULATE) delivered += (int) accepted;
                        return accepted == incoming.getStackSize() ? null
                            : incoming.copy()
                                .setStackSize(incoming.getStackSize() - accepted);
                    }
                    if (method.getName()
                        .equals("jobStateChange")) {
                        check(((ICraftingLink) args[0]).isDone(), "requester received a non-completion notification");
                        notifications++;
                    }
                    return null;
                });
        }

        ICraftingLink submit(int amount, ICraftingRequester requester) {
            BigCraftingJobFast<IAEItemStack> job = new BigCraftingJobFast<>(
                null,
                grid,
                new BaseActionSource(),
                stack(Items.diamond, amount),
                CraftingMode.STANDARD,
                null);
            job.schedule();
            check(
                !job.isSimulation() && job.getErrorMessage()
                    .isEmpty(),
                "self-recursive job planning failed");
            ICraftingLink link = cpu.submitJob(grid, job, new BaseActionSource(), requester);
            check(link != null, "CPU rejected a supplied self-recursive job");
            return link;
        }

        void execute(int rounds) {
            int before = executions;
            for (int tick = 0; tick < 1000 && executions - before < rounds; tick++) tick();
            check(executions - before == rounds, "CPU stopped dispatching recursive pattern before completing tasks");
            check(returned.isEmpty(), "fixture did not return all produced items");
        }

        void tick() {
            cpu.updateCraftingLogic(grid, energy, crafting);
            while (!returned.isEmpty()) {
                IAEStack<?> incoming = returned.remove();
                IAEStack<?> simulated = cpu.injectItems(incoming, Actionable.SIMULATE, new BaseActionSource());
                long before = delivered;
                IAEStack<?> excess = cpu.injectItems(incoming, Actionable.MODULATE, new BaseActionSource());
                if (!returned.isEmpty()) check(!completed(cpu), "CPU completed before all dispatched outputs returned");
                check(
                    (simulated == null ? 0 : simulated.getStackSize()) == (excess == null ? 0 : excess.getStackSize()),
                    "output simulation disagreed with insertion");
                check(delivered >= before, "requester delivery count went backwards");
                if (excess != null) network.injectItems(excess, Actionable.MODULATE);
            }
        }

        long count(Item item) {
            IAEStack<?> value = network.findPrecise(stack(item, 1));
            return value == null ? 0 : value.getStackSize();
        }
    }

    /** Build the actual cluster: older AE releases make it final and keep its fields private. */
    private static CraftingCPUCluster newCPU(IGrid grid) {
        IGridNode node = (IGridNode) Proxy.newProxyInstance(
            IGridNode.class.getClassLoader(),
            new Class<?>[] { IGridNode.class },
            (proxy, method, args) -> {
                if (method.getName()
                    .equals("getGrid")) return grid;
                if (method.getName()
                    .equals("isActive")) return true;
                return null;
            });
        TileCraftingTile core = new TileCraftingTile() {

            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public IGridNode getActionableNode() {
                return node;
            }

            @Override
            public void markDirty() {}

            @Override
            protected ItemStack getItemFromTile(Object tile) {
                return new ItemStack(Items.paper);
            }
        };

        CraftingCPUCluster cpu = new CraftingCPUCluster(
            new appeng.api.util.WorldCoord(0, 0, 0),
            new appeng.api.util.WorldCoord(0, 0, 0));
        try {
            field("machineSrc").set(cpu, new MachineSource(core));
            field("availableStorage").setLong(cpu, 100000);
            ((List<TileCraftingTile>) field("tiles").get(cpu)).add(core);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Self-recursive CPU fixture", error);
        }
        return cpu;
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = CraftingCPUCluster.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static boolean completed(CraftingCPUCluster cpu) {
        try {
            return field("isComplete").getBoolean(cpu);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Self-recursive CPU completion fixture", error);
        }
    }

    private static CraftingCPUCluster.finalOutput outputs(CraftingCPUCluster cpu) {
        try {
            return (CraftingCPUCluster.finalOutput) field("finalOutput").get(cpu);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Self-recursive CPU output fixture", error);
        }
    }

    private static BigInteger balance(CraftingCPUCluster cpu, Item item) {
        return BigAEStackValues.get(outputs(cpu).findPrecise(stack(item, 1)));
    }

    private static void reloadOutput(CraftingCPUCluster cpu) {
        CraftingCPUCluster.finalOutput output = outputs(cpu);
        NBTTagCompound saved = output.writeNbt();
        output.reset();
        output.readFromNBT(saved);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
