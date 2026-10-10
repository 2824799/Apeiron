package com.silvia.apeiron.ae.smoke;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.crafting.core.BigCraftRequest;
import com.silvia.apeiron.ae.crafting.core.BigMECraftingInventory;
import com.silvia.apeiron.ae.crafting.packets.BigCraftPackets;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.stack.InfiniteAEStack;
import com.silvia.apeiron.ae.terminal.BigAmountGui;
import com.silvia.apeiron.ae.terminal.BigGuiNumberCapture;
import com.silvia.apeiron.crafting.BigCraftingJobFast;

import appeng.api.config.CraftingMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IMachineSet;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.core.sync.packets.PacketCraftRequest;
import appeng.crafting.v2.CraftingJobV2;
import appeng.helpers.UltimatePatternHelper;
import appeng.util.item.AEItemStack;
import appeng.util.item.IAEStackList;
import io.netty.buffer.ByteBuf;

/** Real transformed pattern, packet, list and planner checks, independent of GUI input. */
public final class AECraftingPlanningSmoke {

    private AECraftingPlanningSmoke() {}

    public static void verify() {
        IAEItemStack input = AEItemStack.create(new ItemStack(Items.diamond));
        IAEItemStack output = AEItemStack.create(new ItemStack(Items.emerald));
        ItemStack encoded = new ItemStack(Items.paper);
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList in = new NBTTagList();
        NBTTagCompound inputTag = new NBTTagCompound();
        input.writeToNBT(inputTag);
        in.appendTag(inputTag);
        NBTTagList out = new NBTTagList();
        NBTTagCompound outputTag = new NBTTagCompound();
        output.writeToNBT(outputTag);
        out.appendTag(outputTag);
        tag.setTag("in", in);
        tag.setTag("out", out);
        encoded.setTagCompound(tag);
        ICraftingPatternDetails pattern = new UltimatePatternHelper(encoded);
        check(
            BigAEStackValues.get(pattern.getCondensedAEOutputs()[0])
                .equals(BigInteger.ONE),
            "one output became two when decoding the pattern");
        IGrid grid = grid(pattern, input, BigInteger.TEN.pow(60));
        verifyRequestEntry(grid, output);
        verifyPlanningEntrypoint(grid, output);
        verifySelfRecursivePlanning();
        CraftingJobV2<IAEItemStack> nativeJob = new CraftingJobV2<>(
            null,
            grid,
            new BaseActionSource(),
            output.copy(),
            CraftingMode.STANDARD,
            null);
        for (int i = 0; i < 100 && nativeJob.simulateFor(10); i++) {}
        IAEStackList nativePlan = new IAEStackList();
        nativeJob.populatePlan(nativePlan);
        check(
            BigAEStackValues.getCountRequestable(nativePlan.findPrecise(output))
                .equals(BigInteger.ONE),
            "native one-to-one plan requested two outputs");
        for (BigInteger count : new BigInteger[] { BigInteger.ONE, BigInteger.TEN.pow(19), BigInteger.TEN.pow(60) }) {
            IAEItemStack request = BigAEStackValues.copyWithSize(output, count);
            BigCraftingJobFast<IAEItemStack> job = new BigCraftingJobFast<>(
                null,
                grid,
                new BaseActionSource(),
                request,
                CraftingMode.STANDARD,
                null);
            job.schedule();
            check(
                job.getErrorMessage()
                    .isEmpty(),
                "exact planning failed: " + job.getErrorMessage());
            check(!job.isSimulation(), "fully supplied exact plan reported missing inputs");
            IAEStackList plan = new IAEStackList();
            job.populatePlan(plan);
            check(
                BigAEStackValues.getCountRequestable(plan.findPrecise(output))
                    .equals(count),
                "exact one-to-one plan output mismatch");
            check(
                BigAEStackValues.get(plan.findPrecise(input))
                    .equals(count),
                "exact one-to-one plan input mismatch");
        }
        IAEItemStack previous = output.copy();
        ((com.silvia.apeiron.ae.stack.BigAERequestableStack) previous)
            .setCountRequestableCraftsBig(BigInteger.TEN.pow(60));
        long crafts = BigGuiNumberCapture.captureCrafts(previous);
        BigGuiNumberCapture.formatWideAny(crafts);
        long next = BigGuiNumberCapture.captureStack(output.copy());
        check(
            "1".equals(BigGuiNumberCapture.formatWideAny(next)),
            "previous row's craft count leaked into a one-item quantity");
        BigGuiNumberCapture.clear();
        PacketCraftRequest wire = (PacketCraftRequest) BigCraftPackets
            .withExactAmount(requestPacket(Long.MAX_VALUE), BigInteger.TEN.pow(19));
        ByteBuf payload = wire.getProxy()
            .payload();
        try {
            ByteBuf reader = payload.duplicate();
            reader.readInt();
            PacketCraftRequest decoded = new PacketCraftRequest(reader);
            check(
                BigInteger.TEN.pow(19)
                    .equals(((BigCraftRequest) decoded).getCraftAmountBig()),
                "10^19 request packet lost its exact suffix");
        } finally {
            payload.release();
        }
        Apeiron.LOG.info(
            "AE one-to-one pattern, native plan, 10^19/10^60 exact plans and crafting request packet verification passed");
    }

    private static void verifySelfRecursivePlanning() {
        IAEItemStack seed = AEItemStack.create(new ItemStack(Items.diamond));
        IAEItemStack sand = AEItemStack.create(new ItemStack(Items.stick));
        IAEItemStack output = seed.copy();
        output.setStackSize(2);
        ICraftingPatternDetails pattern = pattern(
            new IAEItemStack[] { seed.copy(), sand.copy() },
            new IAEItemStack[] { output });
        IAEItemStack storedSeed = seed.copy();
        storedSeed.setStackSize(1);
        IAEItemStack infiniteSand = sand.copy();
        ((InfiniteAEStack) infiniteSand).setInfinite(true);
        IGrid grid = grid(pattern, Arrays.asList(storedSeed, infiniteSand));
        check(
            BigCraftingJobFast.requiresSelfRecursivePlanner(grid, seed),
            "self-recursive request did not select planner");
        IAEItemStack request = seed.copy();
        request.setStackSize(Long.MAX_VALUE);
        BigAEStackValues.set(request, BigInteger.TEN.pow(19));
        BigCraftingJobFast<IAEItemStack> job = new BigCraftingJobFast<>(
            null,
            grid,
            new BaseActionSource(),
            request,
            CraftingMode.STANDARD,
            null);
        job.schedule();
        check(
            job.getErrorMessage()
                .isEmpty(),
            "self-recursive exact planning failed: " + job.getErrorMessage());
        check(!job.isSimulation(), "self-recursive plan reported missing seed or renewable input");
        final BigInteger[] planned = { BigInteger.ZERO };
        job.forEachPatternBig((ignored, crafts) -> planned[0] = planned[0].add(crafts));
        check(planned[0].equals(BigInteger.TEN.pow(19)), "self-recursive plan used raw output instead of net output");
        IAEStackList plan = new IAEStackList();
        job.populatePlan(plan);
        check(
            BigAEStackValues.get(plan.findPrecise(seed))
                .equals(BigInteger.ONE),
            "self-recursive plan did not reserve one initial seed");
        check(
            BigAEStackValues.get(plan.findPrecise(sand))
                .equals(BigInteger.TEN.pow(19)),
            "self-recursive plan lost external input count");
    }

    private static ICraftingPatternDetails pattern(IAEItemStack[] inputs, IAEItemStack[] outputs) {
        ItemStack encoded = new ItemStack(Items.paper);
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList in = new NBTTagList();
        for (IAEItemStack input : inputs) {
            NBTTagCompound value = new NBTTagCompound();
            input.writeToNBT(value);
            in.appendTag(value);
        }
        NBTTagList out = new NBTTagList();
        for (IAEItemStack output : outputs) {
            NBTTagCompound value = new NBTTagCompound();
            output.writeToNBT(value);
            out.appendTag(value);
        }
        tag.setTag("in", in);
        tag.setTag("out", out);
        encoded.setTagCompound(tag);
        return new UltimatePatternHelper(encoded);
    }

    private static void verifyRequestEntry(IGrid grid, IAEItemStack output) {
        if (cpw.mods.fml.common.FMLCommonHandler.instance()
            .getSide()
            .isServer()) return;
        try {
            final Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            final java.lang.reflect.Field singleton = unsafeClass.getDeclaredField("theUnsafe");
            singleton.setAccessible(true);
            final Object unsafe = singleton.get(null);
            final Object gui = unsafeClass.getMethod("allocateInstance", Class.class)
                .invoke(unsafe, appeng.client.gui.implementations.GuiCraftAmount.class);
            final appeng.client.gui.widgets.MEGuiTextField field = new appeng.client.gui.widgets.MEGuiTextField(
                100,
                18);
            field.setMaxStringLength(32767);
            final java.lang.reflect.Field amount = appeng.client.gui.implementations.GuiAmount.class
                .getDeclaredField("amountTextField");
            amount.setAccessible(true);
            amount.set(gui, field);
            final java.lang.reflect.Method getLong = appeng.client.gui.implementations.GuiAmount.class
                .getDeclaredMethod("getAmountLong");
            final java.lang.reflect.Method getInt = appeng.client.gui.implementations.GuiAmount.class
                .getDeclaredMethod("getAmount");
            getLong.setAccessible(true);
            getInt.setAccessible(true);
            final java.lang.reflect.Method append = java.util.Arrays.stream(
                gui.getClass()
                    .getDeclaredMethods())
                .filter(
                    method -> method.getName()
                        .endsWith("apeiron$appendExactAmount"))
                .findFirst()
                .get();
            append.setAccessible(true);
            for (BigInteger requested : new BigInteger[] { BigInteger.TEN.pow(19), BigInteger.TEN.pow(60),
                BigInteger.TEN.pow(600) }) {
                field.setText(requested.toString());
                check((Integer) getInt.invoke(gui) > 0, "large request disabled the amount button");
                check(requested.equals(((BigAmountGui) gui).getAmountBig()), "amount field lost exact input");
                PacketCraftRequest sent = (PacketCraftRequest) append
                    .invoke(gui, requestPacket((Long) getLong.invoke(gui)));
                ByteBuf payload = sent.getProxy()
                    .payload();
                PacketCraftRequest decoded;
                try {
                    ByteBuf reader = payload.duplicate();
                    reader.readInt();
                    decoded = new PacketCraftRequest(reader);
                } finally {
                    payload.release();
                }
                final java.lang.reflect.Method apply = java.util.Arrays.stream(
                    decoded.getClass()
                        .getDeclaredMethods())
                    .filter(
                        method -> method.getName()
                            .endsWith("apeiron$setExactCraftAmount"))
                    .findFirst()
                    .get();
                apply.setAccessible(true);
                IAEItemStack request = (IAEItemStack) apply.invoke(decoded, output.copy(), Long.MAX_VALUE);
                check(
                    BigAEStackValues.get(request)
                        .equals(requested),
                    "server packet applied a saturated request");
                appeng.api.networking.crafting.ICraftingJob<?> job = beginJob(grid, request);
                check(
                    job instanceof BigCraftingJobFast && ((BigCraftingJobFast<?>) job).getErrorMessage()
                        .isEmpty(),
                    "request entered the long-only planner");
                check(
                    BigAEStackValues.get(job.getOutput())
                        .equals(requested),
                    "planner output was truncated");
            }
            appeng.crafting.MECraftingInventory inventory = new appeng.crafting.MECraftingInventory();
            ((BigMECraftingInventory) inventory).injectItemsBig(
                BigAEStackValues.copyWithSize(output, BigInteger.TEN.pow(60)),
                appeng.api.config.Actionable.MODULATE);
            IAEStack<?> snapshot = ((BigMECraftingInventory) inventory).getStoredStackBig(output);
            check(
                BigAEStackValues.get(snapshot)
                    .equals(BigInteger.TEN.pow(60)),
                "available inventory stopped at long max");
            snapshot.reset();
            check(
                BigAEStackValues.get(((BigMECraftingInventory) inventory).getStoredStackBig(output))
                    .equals(BigInteger.TEN.pow(60)),
                "availability query mutated the model");
            final appeng.container.implementations.ContainerCraftConfirm confirm = (appeng.container.implementations.ContainerCraftConfirm) unsafeClass
                .getMethod("allocateInstance", Class.class)
                .invoke(unsafe, appeng.container.implementations.ContainerCraftConfirm.class);
            final java.lang.reflect.Field result = confirm.getClass()
                .getDeclaredField("result");
            result.setAccessible(true);
            result.set(
                confirm,
                Proxy.newProxyInstance(
                    appeng.api.networking.crafting.ICraftingJob.class.getClassLoader(),
                    new Class<?>[] { appeng.api.networking.crafting.ICraftingJob.class },
                    (proxy, method, args) -> method.getName()
                        .equals("getStorageAtBeginning") ? inventory : null));
            final java.lang.reflect.Method percent = java.util.Arrays.stream(
                confirm.getClass()
                    .getDeclaredMethods())
                .filter(
                    method -> method.getName()
                        .endsWith("apeiron$exactUsedPercent"))
                .findFirst()
                .get();
            percent.setAccessible(true);
            IAEStack<?> used = (IAEStack<?>) percent.invoke(
                confirm,
                BigAEStackValues.copyWithSize(
                    output,
                    BigInteger.TEN.pow(60)
                        .divide(BigInteger.valueOf(4))),
                100f);
            check(used.getUsedPercent() == 25f, "confirm percentage used saturated inventory");
            final java.lang.reflect.Method simulate = java.util.Arrays.stream(
                confirm.getClass()
                    .getDeclaredMethods())
                .filter(
                    method -> method.getName()
                        .endsWith("apeiron$simulateExactMissing"))
                .findFirst()
                .get();
            final java.lang.reflect.Method remainder = java.util.Arrays.stream(
                confirm.getClass()
                    .getDeclaredMethods())
                .filter(
                    method -> method.getName()
                        .endsWith("apeiron$exactMissingRemainder"))
                .findFirst()
                .get();
            simulate.setAccessible(true);
            remainder.setAccessible(true);
            IAEStack<?> request = BigAEStackValues.copyWithSize(
                output,
                BigInteger.TEN.pow(60)
                    .multiply(BigInteger.valueOf(2)));
            simulate.invoke(confirm, inventory, request, appeng.api.config.Actionable.SIMULATE, new BaseActionSource());
            IAEStack<?> missing = (IAEStack<?>) remainder.invoke(confirm, request.copy(), 0L);
            check(
                BigAEStackValues.get(missing)
                    .equals(BigInteger.TEN.pow(60)),
                "missing preview subtracted two saturated longs");
            check(
                BigAEStackValues.get(((BigMECraftingInventory) inventory).getStoredStackBig(output))
                    .equals(BigInteger.TEN.pow(60)),
                "missing preview consumed inventory");
        } catch (Exception error) {
            throw new IllegalStateException("Craft amount entry/packet/planner verification failed", error);
        }
    }

    private static PacketCraftRequest requestPacket(long amount) {
        for (java.lang.reflect.Constructor<?> constructor : PacketCraftRequest.class.getConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length < 4 || parameters[0] != long.class) continue;
            try {
                return (PacketCraftRequest) constructor.newInstance(
                    parameters.length == 5 ? new Object[] { amount, false, false, CraftingMode.STANDARD, false }
                        : new Object[] { amount, false, false, CraftingMode.STANDARD });
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot construct crafting request", error);
            }
        }
        throw new IllegalStateException("Unknown AE crafting request constructor");
    }

    private static void verifyPlanningEntrypoint(IGrid grid, IAEItemStack output) {
        try {
            for (BigInteger amount : new BigInteger[] { BigInteger.TEN.pow(19), BigInteger.TEN.pow(60),
                BigInteger.TEN.pow(600) }) {
                IAEItemStack request = BigAEStackValues.copyWithSize(output, amount);
                appeng.api.networking.crafting.ICraftingJob<?> result = beginJob(grid, request);
                check(
                    result instanceof BigCraftingJobFast && ((BigCraftingJobFast<?>) result).getErrorMessage()
                        .isEmpty(),
                    "native request did not select the exact planner");
                check(
                    BigAEStackValues.get(result.getOutput())
                        .equals(amount),
                    "native request was truncated");
            }
        } catch (Exception error) {
            throw new IllegalStateException("Native crafting entrypoint verification failed", error);
        }
    }

    private static appeng.api.networking.crafting.ICraftingJob<?> beginJob(IGrid grid, IAEItemStack request)
        throws Exception {
        appeng.me.cache.CraftingGridCache cache = new appeng.me.cache.CraftingGridCache(grid);
        java.lang.reflect.Method entry = java.util.Arrays.stream(
            cache.getClass()
                .getMethods())
            .filter(
                method -> method.getName()
                    .equals("beginCraftingJob") && method.getParameterCount() >= 6)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing native crafting entrypoint"));
        Object[] arguments = entry.getParameterCount() == 7
            ? new Object[] { null, grid, new BaseActionSource(), request, CraftingMode.STANDARD, false, null }
            : new Object[] { null, grid, new BaseActionSource(), request, CraftingMode.STANDARD, null };
        return (appeng.api.networking.crafting.ICraftingJob<?>) ((java.util.concurrent.Future<?>) entry
            .invoke(cache, arguments)).get();
    }

    private static IGrid grid(ICraftingPatternDetails pattern, IAEItemStack input, BigInteger amount) {
        return grid(pattern, Arrays.asList(BigAEStackValues.copyWithSize(input, amount)));
    }

    private static IGrid grid(ICraftingPatternDetails pattern, List<IAEItemStack> stored) {
        ImmutableMap<IAEStack<?>, ImmutableList<ICraftingPatternDetails>> patterns = ImmutableMap
            .of(pattern.getCondensedAEOutputs()[0], ImmutableList.of(pattern));
        ICraftingGrid crafting = (ICraftingGrid) Proxy.newProxyInstance(
            ICraftingGrid.class.getClassLoader(),
            new Class<?>[] { ICraftingGrid.class },
            (proxy, method, args) -> {
                if (method.getName()
                    .equals("getCraftingMultiPatterns")) return patterns;
                if (method.getReturnType() == boolean.class) return false;
                return null;
            });
        IStorageGrid storage = (IStorageGrid) Proxy.newProxyInstance(
            IStorageGrid.class.getClassLoader(),
            new Class<?>[] { IStorageGrid.class },
            (proxy, method, args) -> {
                if (method.getName()
                    .equals("getMEMonitor")) {
                    IAEStackType<?> type = (IAEStackType<?>) args[0];
                    IItemList list = type.createList();
                    for (IAEItemStack candidate : stored)
                        if (type == candidate.getStackType()) list.add(candidate.copy());
                    return Proxy.newProxyInstance(
                        IMEMonitor.class.getClassLoader(),
                        new Class<?>[] { IMEMonitor.class },
                        (monitor, call, values) -> {
                            if (call.getName()
                                .equals("getStorageList")) return list;
                            return null;
                        });
                }
                return null;
            });
        return (IGrid) Proxy
            .newProxyInstance(IGrid.class.getClassLoader(), new Class<?>[] { IGrid.class }, (proxy, method, args) -> {
                if (method.getName()
                    .equals("getCache"))
                    return args[0] == ICraftingGrid.class ? crafting : args[0] == IStorageGrid.class ? storage : null;
                if (method.getName()
                    .equals("getMachines")) {
                    final Class<?> machineClass = (Class<?>) args[0];
                    return Proxy.newProxyInstance(
                        IMachineSet.class.getClassLoader(),
                        new Class<?>[] { IMachineSet.class },
                        (machines, call, values) -> {
                            if (call.getName()
                                .equals("getMachineClass")) return machineClass;
                            if (call.getName()
                                .equals("isEmpty")) return true;
                            if (call.getName()
                                .equals("size")) return 0;
                            if (call.getName()
                                .equals("contains")) return false;
                            if (call.getName()
                                .equals("iterator"))
                                return java.util.Collections.<IGridNode>emptyList()
                                    .iterator();
                            return null;
                        });
                }
                return null;
            });
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
