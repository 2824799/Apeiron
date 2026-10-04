package com.silvia.apeiron.ae.smoke;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.crafting.core.BigMECraftingInventory;
import com.silvia.apeiron.ae.crafting.core.CraftingTreeSource;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.crafting.BigCraftingJobFast;
import com.silvia.apeiron.crafting.BigCraftingTree;

import appeng.api.AEApi;
import appeng.api.config.CraftingMode;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IMachineSet;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.client.gui.widgets.GuiCraftingTree;
import appeng.core.sync.packets.PacketCraftingTreeData;
import appeng.crafting.fast.CraftingJobFast;
import appeng.crafting.v2.CraftingJobV2;
import appeng.crafting.v2.CraftingRequest;
import appeng.crafting.v2.CraftingRequest.UsedResolverEntry;
import appeng.crafting.v2.resolvers.CraftableItemResolver.CraftFromPatternTask;
import appeng.crafting.v2.resolvers.ExtractItemResolver.ExtractItemTask;
import appeng.crafting.v2.resolvers.IgnoreMissingItemResolver.IgnoreMissingItemTask;
import appeng.crafting.v2.resolvers.SimulateMissingItemResolver.ConjureItemTask;
import appeng.util.item.AEItemStack;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Round-trips both fast planners through AE2's actual compressed packet and native GUI node builder. */
@SuppressWarnings({ "rawtypes", "unchecked" })
public final class AECraftingTreeSmoke {

    private AECraftingTreeSmoke() {}

    public static void verify() {
        try {
            Class.forName("appeng.container.implementations.ContainerCraftConfirm")
                .getDeclaredMethods();
            IAEItemStack diamond = AEItemStack.create(new ItemStack(Items.diamond));
            IAEItemStack emerald = AEItemStack.create(new ItemStack(Items.emerald));
            IAEItemStack gold = AEItemStack.create(new ItemStack(Items.gold_ingot));
            ICraftingPatternDetails first = pattern(diamond, emerald);
            ICraftingPatternDetails second = pattern(emerald, gold);
            for (BigInteger count : new BigInteger[] { BigInteger.ONE, BigInteger.TEN.pow(19),
                BigInteger.TEN.pow(60) }) {
                final IGrid grid = grid(Arrays.asList(first, second), diamond, count);
                final BigCraftingJobFast job = new BigCraftingJobFast(
                    null,
                    grid,
                    new BaseActionSource(),
                    BigAEStackValues.copyWithSize(gold, count),
                    CraftingMode.STANDARD,
                    null);
                job.schedule();
                check(
                    job.getErrorMessage()
                        .isEmpty() && !job.isSimulation(),
                    "exact planner did not complete");
                final BigCraftingTree tree = roundTrip(job.getJobTree());
                check(
                    BigAEStackValues.get(tree.originalRequest.stack)
                        .equals(count),
                    "root amount was truncated");
                check(
                    tree.getExactBytes()
                        .equals(job.getByteTotalBig()),
                    "tree byte count was truncated");
                final CraftingRequest middle = craftedChild(tree.originalRequest, count);
                final CraftingRequest leaf = craftedChild(middle, count);
                final UsedResolverEntry extracted = leaf.usedResolvers.get(0);
                check(
                    extracted.task instanceof ExtractItemTask && BigAEStackValues.get(extracted.resolvedStack)
                        .equals(count),
                    "stored material node missing or truncated");
                check(!leaf.wasSimulated, "available material was marked missing");
                check(
                    BigAEStackValues
                        .get(((BigMECraftingInventory) job.getStorageAtBeginning()).getStoredStackBig(diamond))
                        .equals(count),
                    "display tree mutated storage");
                verifyLayout(tree.originalRequest, false);
            }

            final IGrid grid = grid(Collections.singletonList(first), diamond, BigInteger.valueOf(64));
            if (com.silvia.apeiron.compat.DependencyCapabilities.hasClass("appeng.crafting.fast.CraftingJobFast")) {
                final ICraftingJob<?> nativeJob = new CraftingJobFast<>(
                    null,
                    grid,
                    new BaseActionSource(),
                    emerald.copy(),
                    CraftingMode.STANDARD,
                    null).schedule()
                        .get();
                check(nativeJob instanceof CraftingTreeSource, "native fast planner cannot supply a tree");
                craftedChild(roundTrip(((CraftingTreeSource) nativeJob).getJobTree()).originalRequest, BigInteger.ONE);
            }

            final CraftingJobV2<IAEItemStack> original = new CraftingJobV2<>(
                null,
                grid,
                new BaseActionSource(),
                emerald.copy(),
                CraftingMode.STANDARD,
                null);
            for (int step = 0; step < 100 && original.simulateFor(10); step++) {}
            final ByteBuf nativeData = original.serialize();
            try {
                check(
                    CraftingJobV2.deserialize(null, nativeData).originalRequest.usedResolvers.size() > 0,
                    "native detailed tree protocol regressed");
            } finally {
                nativeData.release();
            }

            for (CraftingMode mode : CraftingMode.values()) {
                final BigInteger requested = BigInteger.TEN.pow(60);
                final BigCraftingJobFast job = new BigCraftingJobFast(
                    null,
                    grid(Collections.singletonList(first), diamond, requested.subtract(BigInteger.ONE)),
                    new BaseActionSource(),
                    BigAEStackValues.copyWithSize(emerald, requested),
                    mode,
                    null);
                job.schedule();
                final BigCraftingTree tree = roundTrip(job.getJobTree());
                final CraftingRequest leaf = craftedChild(tree.originalRequest, requested);
                check(
                    leaf.usedResolvers.size() == 2 && leaf.wasSimulated,
                    "partial availability lost its missing node");
                final UsedResolverEntry missing = leaf.usedResolvers.get(1);
                check(
                    BigAEStackValues.get(missing.resolvedStack)
                        .equals(BigInteger.ONE),
                    "missing count changed");
                check(
                    mode == CraftingMode.IGNORE_MISSING ? missing.task instanceof IgnoreMissingItemTask
                        : missing.task instanceof ConjureItemTask,
                    "missing node uses wrong mode");
                verifyLayout(tree.originalRequest, true);
            }
            Apeiron.LOG.info(
                "Crafting tree verification passed: native/precise planners, compressed packets, exact counts and missing materials");
            if (cpw.mods.fml.common.FMLCommonHandler.instance()
                .getSide()
                .isClient())
                Apeiron.LOG
                    .info("Crafting tree native GUI layout, missing colors and animated quantity verification passed");
        } catch (Exception e) {
            throw new IllegalStateException("Crafting tree verification failed", e);
        }
    }

    private static CraftingRequest craftedChild(CraftingRequest request, BigInteger count) {
        final UsedResolverEntry entry = request.usedResolvers.get(0);
        check(entry.task instanceof BigCraftingTree.PatternTask, "pattern node missing");
        check(
            ((BigCraftingTree.PatternTask) entry.task).getExactCrafts()
                .equals(count),
            "operation count truncated");
        check(
            BigAEStackValues.get(entry.resolvedStack)
                .equals(count),
            "resolved amount truncated");
        final List<CraftingRequest> children = ((CraftFromPatternTask) entry.task).getChildRequests();
        check(children.size() == 1, "recipe input node missing");
        check(
            BigAEStackValues.get(children.get(0).stack)
                .equals(count),
            "recipe input amount truncated");
        return children.get(0);
    }

    private static BigCraftingTree roundTrip(CraftingJobV2<?> tree) throws Exception {
        final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        final List<PacketCraftingTreeData> chunks = PacketCraftingTreeData.createChunks(tree);
        check(!chunks.isEmpty(), "no tree packet sent");
        for (int i = 0; i < chunks.size(); i++) {
            final ByteBuf payload = chunks.get(i)
                .getProxy()
                .payload();
            try {
                payload.readInt();
                check(payload.readInt() == i && payload.readInt() == chunks.size(), "invalid native chunk header");
                byte[] bytes = new byte[payload.readableBytes()];
                payload.readBytes(bytes);
                compressed.write(bytes);
            } finally {
                payload.release();
            }
        }
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed.toByteArray()))) {
            final ByteArrayOutputStream uncompressed = new ByteArrayOutputStream();
            byte[] bytes = new byte[4096];
            int size;
            while ((size = gzip.read(bytes)) >= 0) uncompressed.write(bytes, 0, size);
            final ByteBuf packet = Unpooled.wrappedBuffer(uncompressed.toByteArray())
                .order(ByteOrder.LITTLE_ENDIAN);
            try {
                final CraftingJobV2<?> decoded = CraftingJobV2.deserialize(null, packet);
                check(decoded instanceof BigCraftingTree && !packet.isReadable(), "tree not fully decoded");
                return (BigCraftingTree) decoded;
            } finally {
                packet.release();
            }
        }
    }

    private static void verifyLayout(CraftingRequest request, boolean missing) throws Exception {
        if (!cpw.mods.fml.common.FMLCommonHandler.instance()
            .getSide()
            .isClient()) return;
        final GuiCraftingTree gui = new GuiCraftingTree(null, 0, 0, 300, 200);
        gui.setRequest(request);
        java.lang.reflect.Field nodesField;
        boolean modern;
        try {
            nodesField = GuiCraftingTree.class.getDeclaredField("allNodes");
            modern = true;
        } catch (NoSuchFieldException oldLayout) {
            nodesField = GuiCraftingTree.class.getDeclaredField("treeNodes");
            modern = false;
        }
        nodesField.setAccessible(true);
        List<?> nodes = modern ? (List<?>) nodesField.get(gui)
            : (List<?>) ((java.util.Map<?, ?>) nodesField.get(gui)).values()
                .stream()
                .flatMap(row -> ((List<?>) row).stream())
                .collect(java.util.stream.Collectors.toList());
        check(nodes.size() >= 4, "native renderer produced an empty tree");
        if (modern) {
            final java.lang.reflect.Field hasMissing = nodes.get(0)
                .getClass()
                .getSuperclass()
                .getDeclaredField("hasMissing");
            hasMissing.setAccessible(true);
            check(hasMissing.getBoolean(nodes.get(0)) == missing, "native missing color did not propagate");
        } else {
            boolean missingNode = false;
            for (Object node : nodes) {
                if (!node.getClass()
                    .getSimpleName()
                    .equals("RequestNode")) continue;
                java.lang.reflect.Field nodeRequest = node.getClass()
                    .getDeclaredField("request");
                nodeRequest.setAccessible(true);
                missingNode |= ((CraftingRequest) nodeRequest.get(node)).wasSimulated;
            }
            check(missingNode == missing, "legacy tree lost missing material color");
        }
        final java.lang.reflect.Method display = GuiCraftingTree.class
            .getDeclaredMethod("getDisplayItemForRequest", CraftingRequest.class);
        display.setAccessible(true);
        check(
            BigAEStackValues.get((IAEStack<?>) display.invoke(gui, request))
                .equals(BigAEStackValues.get(request.stack)),
            "animated node display truncated its quantity");
        if (modern) gui.hideAvailable();
        gui.setRequest(request);
    }

    private static ICraftingPatternDetails pattern(IAEStack<?> input, IAEStack<?> output) {
        final ItemStack encoded = AEApi.instance()
            .definitions()
            .items()
            .encodedPattern()
            .maybeStack(1)
            .get();
        final NBTTagCompound tag = new NBTTagCompound();
        final NBTTagList inputs = new NBTTagList();
        final NBTTagList outputs = new NBTTagList();
        final NBTTagCompound in = new NBTTagCompound();
        final NBTTagCompound out = new NBTTagCompound();
        input.writeToNBT(in);
        output.writeToNBT(out);
        inputs.appendTag(in);
        outputs.appendTag(out);
        tag.setTag("in", inputs);
        tag.setTag("out", outputs);
        encoded.setTagCompound(tag);
        return ((ICraftingPatternItem) encoded.getItem()).getPatternForItem(encoded, null);
    }

    private static IGrid grid(List<ICraftingPatternDetails> recipes, IAEItemStack input, BigInteger amount) {
        final ImmutableMap.Builder<IAEStack<?>, ImmutableList<ICraftingPatternDetails>> builder = ImmutableMap
            .builder();
        for (ICraftingPatternDetails pattern : recipes)
            builder.put(pattern.getCondensedAEOutputs()[0], ImmutableList.of(pattern));
        final ImmutableMap<IAEStack<?>, ImmutableList<ICraftingPatternDetails>> patterns = builder.build();
        final ICraftingGrid crafting = (ICraftingGrid) Proxy.newProxyInstance(
            ICraftingGrid.class.getClassLoader(),
            new Class<?>[] { ICraftingGrid.class },
            (proxy, method, args) -> {
                if (method.getName()
                    .equals("getCraftingMultiPatterns")) return patterns;
                return method.getReturnType() == boolean.class ? false : null;
            });
        final IStorageGrid storage = (IStorageGrid) Proxy.newProxyInstance(
            IStorageGrid.class.getClassLoader(),
            new Class<?>[] { IStorageGrid.class },
            (proxy, method, args) -> {
                if (!method.getName()
                    .equals("getMEMonitor")) return null;
                final IAEStackType<?> type = (IAEStackType<?>) args[0];
                final IItemList list = type.createList();
                if (type == input.getStackType()) list.add(BigAEStackValues.copyWithSize(input, amount));
                return Proxy.newProxyInstance(
                    IMEMonitor.class.getClassLoader(),
                    new Class<?>[] { IMEMonitor.class },
                    (monitor, call, values) -> call.getName()
                        .equals("getStorageList") ? list : null);
            });
        return (IGrid) Proxy
            .newProxyInstance(IGrid.class.getClassLoader(), new Class<?>[] { IGrid.class }, (proxy, method, args) -> {
                if (method.getName()
                    .equals("getCache"))
                    return args[0] == ICraftingGrid.class ? crafting : args[0] == IStorageGrid.class ? storage : null;
                if (method.getName()
                    .equals("getMachines")) {
                    final Class<?> type = (Class<?>) args[0];
                    return Proxy.newProxyInstance(
                        IMachineSet.class.getClassLoader(),
                        new Class<?>[] { IMachineSet.class },
                        (machines, call, values) -> {
                            if (call.getName()
                                .equals("getMachineClass")) return type;
                            if (call.getName()
                                .equals("isEmpty")) return true;
                            if (call.getName()
                                .equals("size")) return 0;
                            if (call.getName()
                                .equals("contains")) return false;
                            if (call.getName()
                                .equals("iterator"))
                                return Collections.<IGridNode>emptyList()
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
