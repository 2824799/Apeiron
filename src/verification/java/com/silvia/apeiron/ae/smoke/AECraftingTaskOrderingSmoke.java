package com.silvia.apeiron.ae.smoke;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidRegistry;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.crafting.core.BigCraftingCPU;
import com.silvia.apeiron.ae.crafting.core.BigTaskProgress;
import com.silvia.apeiron.ae.crafting.core.CraftingPatternComparator;
import com.silvia.apeiron.common.machine.me.input.pattern.MultipliedPatternDetails;

import appeng.api.AEApi;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.helpers.UltimatePatternHelper;
import appeng.me.cluster.implementations.CraftingCPUCluster;

/** Saved plate recipes and registry-independent NBT collisions through the transformed CPU. */
public final class AECraftingTaskOrderingSmoke {

    private AECraftingTaskOrderingSmoke() {}

    public static void verify() {
        if (!com.silvia.apeiron.compat.DependencyCapabilities.hasDeclaredField(
            "appeng.me.cluster.implementations.CraftingCPUCluster",
            "priorityComparator",
            "Ljava/util/Comparator;")) return;
        try {
            for (int[] values : new int[][] { { 17132, 6132, 17129, 6129, 131072 },
                { 17139, 6139, 17141, 6141, 256 } }) {
                ICraftingPatternDetails left = multiplied(values[0], values[1], values[4]);
                ICraftingPatternDetails right = multiplied(values[2], values[3], values[4]);
                check(!left.equals(right), "fixture recipes unexpectedly equal");
                verifyCPU(left, right);
                verifyCPU(right, left);
                AESelfRecursiveCraftingSmoke.verifyCollidingExecution(left, right);
                AESelfRecursiveCraftingSmoke.verifyCollidingRollback(left, right);
            }
            for (int multiplier : new int[] { 256, 131072 }) {
                ICraftingPatternDetails left = collisionVariant("Aa", multiplier);
                ICraftingPatternDetails right = collisionVariant("BB", multiplier);
                check(left.hashCode() == right.hashCode() && !left.equals(right), "NBT collision fixture failed");
                verifyCPU(left, right);
                verifyCPU(right, left);
                AESelfRecursiveCraftingSmoke.verifyCollidingExecution(left, right);
                AESelfRecursiveCraftingSmoke.verifyCollidingRollback(left, right);
            }
            if (cpw.mods.fml.common.Loader.isModLoaded("programmablehatches")) Programmable.verify();
            benchmark();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("AE task collision verification failed", error);
        }
        Apeiron.LOG.info(
            "AE task ordering: saved plate recipes, guaranteed NBT collisions, equal decoded keys, work/backup maps, reversed reload, exact counts and cancellation passed");
    }

    private static ICraftingPatternDetails multiplied(int input, int output, int multiplier) {
        return new MultipliedPatternDetails(base(input, output), BigInteger.valueOf(multiplier));
    }

    private static ICraftingPatternDetails collisionVariant(String variant, int multiplier) {
        // Saved numeric item IDs change in an isolated profile. Aa and BB have the same String
        // hash, so these genuinely different ingredient/output NBTs collide in every registry.
        ItemStack item = base(17139, 6139).getPattern()
            .copy();
        for (String side : new String[] { "in", "out" }) {
            NBTTagCompound properties = new NBTTagCompound();
            properties.setString("variant", variant);
            item.getTagCompound()
                .getTagList(side, 10)
                .getCompoundTagAt(0)
                .setTag("tag", properties);
        }
        return new MultipliedPatternDetails(new UltimatePatternHelper(item), BigInteger.valueOf(multiplier));
    }

    /** Optional local benchmark: no save data or registry-dependent fixtures are bundled in CI. */
    private static void benchmark() {
        String path = System.getenv("APEIRON_TASK_BENCHMARK_FIXTURE");
        if (path == null) return;
        try (FileInputStream stream = new FileInputStream(path)) {
            NBTTagList encoded = CompressedStreamTools.readCompressed(stream)
                .getTagList("patterns", 10);
            List<ICraftingPatternDetails> patterns = new ArrayList<>();
            for (int i = 0; i < encoded.tagCount(); i++) {
                NBTTagCompound tag = (NBTTagCompound) encoded.getCompoundTagAt(i)
                    .copy();
                if (!resolveRegistry(tag)) continue;
                ItemStack item = ItemStack.loadItemStackFromNBT(tag);
                if (item == null || !(item.getItem() instanceof ICraftingPatternItem)) continue;
                try {
                    ICraftingPatternDetails details = ((ICraftingPatternItem) item.getItem())
                        .getPatternForItem(item, null);
                    if (details != null) patterns.add(new MultipliedPatternDetails(details, BigInteger.valueOf(256)));
                } catch (RuntimeException invalid) {
                    // A partial development profile cannot decode every optional mod's recipes.
                }
            }
            check(patterns.size() >= 1000, "too few saved patterns for a meaningful task-ordering benchmark");
            Collections.shuffle(patterns, new java.util.Random(17));
            long[] baseline = new long[21];
            long[] fixed = new long[21];
            int[] sizes = new int[2];
            for (int i = -7; i < baseline.length; i++) {
                // Alternate order to avoid systematically giving either comparator a cold JVM.
                for (int j = 0; j < 2; j++) {
                    boolean safe = ((i + j) & 1) == 0;
                    Comparator<ICraftingPatternDetails> comparator = safe ? new CraftingPatternComparator()
                        : Comparator.comparing(ICraftingPatternDetails::getPriority)
                            .thenComparing(ICraftingPatternDetails::hashCode);
                    long start = System.nanoTime();
                    Map<ICraftingPatternDetails, Long> tasks = new TreeMap<>(comparator);
                    for (ICraftingPatternDetails pattern : patterns) {
                        Long count = tasks.get(pattern);
                        tasks.put(pattern, count == null ? 1L : count + 1);
                    }
                    Map<ICraftingPatternDetails, Long> work = new TreeMap<>(comparator);
                    work.putAll(tasks);
                    Map<ICraftingPatternDetails, Long> backup = new TreeMap<>(comparator);
                    backup.putAll(tasks);
                    long elapsed = System.nanoTime() - start;
                    check(work.size() == tasks.size() && backup.size() == tasks.size(), "benchmark copy lost tasks");
                    sizes[safe ? 1 : 0] = tasks.size();
                    if (i >= 0) (safe ? fixed : baseline)[i] = elapsed;
                }
            }
            java.util.Arrays.sort(baseline);
            java.util.Arrays.sort(fixed);
            Apeiron.LOG.info(
                "Saved-pattern task ordering benchmark: decoded={}/{}, nativeTasks={}, safeTasks={}, nativeMedianMs={}, safeMedianMs={} (same cached pattern hashes; task registration + execution/backup copies, not full planning)",
                patterns.size(),
                encoded.tagCount(),
                sizes[0],
                sizes[1],
                baseline[10] / 1_000_000.0,
                fixed[10] / 1_000_000.0);
        } catch (IOException error) {
            throw new IllegalStateException("Saved-pattern task-ordering benchmark failed", error);
        }
    }

    private static boolean resolveRegistry(NBTBase tag) {
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            if (compound.hasKey("id", 8)) {
                Item item = (Item) Item.itemRegistry.getObject(compound.getString("id"));
                if (item == null) return false;
                compound.setShort("id", (short) Item.getIdFromItem(item));
            }
            if (compound.hasKey("FluidName", 8) && FluidRegistry.getFluid(compound.getString("FluidName")) == null)
                return false;
            for (Object key : compound.func_150296_c())
                if (!resolveRegistry(compound.getTag((String) key))) return false;
        } else if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag;
            for (int i = 0; i < list.tagCount(); i++) if (!resolveRegistry(list.getCompoundTagAt(i))) return false;
        }
        return true;
    }

    private static ICraftingPatternDetails base(int input, int output) {
        ItemStack encoded = AEApi.instance()
            .definitions()
            .items()
            .encodedUltimatePattern()
            .maybeStack(1)
            .get();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("in", stackList("gregtech:gt.metaitem.01", input, 64));
        tag.setTag("out", stackList("gregtech:gt.metaitem.03", output, 1));
        tag.setBoolean("crafting", false);
        tag.setBoolean("substitute", false);
        tag.setBoolean("beSubstitute", false);
        encoded.setTagCompound(tag);
        return new UltimatePatternHelper(encoded);
    }

    private static NBTTagList stackList(String name, int damage, int count) {
        Item item = (Item) Item.itemRegistry.getObject(name);
        check(item != null, "missing fixture item: " + name);
        NBTTagCompound stack = new ItemStack(item, count, damage).writeToNBT(new NBTTagCompound());
        stack.setInteger("Count", count);
        stack.setLong("Cnt", count);
        NBTTagList list = new NBTTagList();
        list.appendTag(stack);
        return list;
    }

    private static void verifyCPU(ICraftingPatternDetails left, ICraftingPatternDetails right)
        throws ReflectiveOperationException {
        CraftingCPUCluster cpu = AESelfRecursiveCraftingSmoke.newCPU(null);
        BigCraftingCPU exact = (BigCraftingCPU) (Object) cpu;
        BigInteger leftCount = BigInteger.TEN.pow(60)
            .add(BigInteger.valueOf(17));
        BigInteger rightCount = BigInteger.TEN.pow(65)
            .add(BigInteger.valueOf(31));
        exact.addCraftingBig(left, leftCount);
        exact.addCraftingBig(right, rightCount);
        exact.addCraftingBig(decode(left), BigInteger.valueOf(3));
        leftCount = leftCount.add(BigInteger.valueOf(3));
        check(
            exact.getTaskEntriesBig()
                .size() == 2,
            "CPU merged distinct recipes with colliding hashes");
        check(count(cpu, left).equals(leftCount) && count(cpu, right).equals(rightCount), "CPU mixed task counts");

        Comparator<? super ICraftingPatternDetails> comparator = ((TreeMap<ICraftingPatternDetails, ?>) exact
            .getTaskEntriesBig()).comparator();
        check(comparator instanceof CraftingPatternComparator, "CPU still uses unsafe native comparator");
        Field workField = CraftingCPUCluster.class.getDeclaredField("workableTasks");
        workField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> work = (Map<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress>) workField
            .get(cpu);
        work.putAll(exact.getTaskEntriesBig());
        check(work.size() == 2, "workable task copy merged recipes");
        Map<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> backup = new TreeMap<>(comparator);
        backup.putAll(exact.getTaskEntriesBig());
        exact.getTaskEntriesBig()
            .clear();
        exact.getTaskEntriesBig()
            .putAll(backup);
        check(
            count(cpu, left).equals(leftCount) && count(cpu, right).equals(rightCount),
            "backup restore mixed recipes");

        NBTTagCompound saved = new NBTTagCompound();
        cpu.writeToNBT(saved);
        NBTTagList tasks = saved.getTagList("tasks", 10);
        NBTTagList reversed = new NBTTagList();
        for (int i = tasks.tagCount() - 1; i >= 0; i--) reversed.appendTag(
            tasks.getCompoundTagAt(i)
                .copy());
        saved.setTag("tasks", reversed);
        CraftingCPUCluster restored = AESelfRecursiveCraftingSmoke.newCPU(null);
        restored.readFromNBT(saved);
        check(
            ((BigCraftingCPU) (Object) restored).getTaskEntriesBig()
                .size() == 2,
            "reload merged distinct collision tasks");
        check(
            count(restored, decode(left)).equals(leftCount) && count(restored, decode(right)).equals(rightCount),
            "reload assigned exact progress to the wrong pattern");
        restored.cancel();
        check(
            !restored.isBusy() && restored.getInventory()
                .isEmpty(),
            "cancelled collision job retained tasks or items");
    }

    private static ICraftingPatternDetails decode(ICraftingPatternDetails pattern) {
        ItemStack item = pattern.getPattern();
        return ((ICraftingPatternItem) item.getItem()).getPatternForItem(item.copy(), null);
    }

    private static BigInteger count(CraftingCPUCluster cpu, ICraftingPatternDetails pattern) {
        CraftingCPUCluster.TaskProgress progress = ((BigCraftingCPU) (Object) cpu).getTaskEntriesBig()
            .get(pattern);
        check(progress != null, "task missing after hash collision");
        return ((BigTaskProgress) progress).getValueBig();
    }

    private static final class Programmable {

        private static void verify() {
            ICraftingPatternDetails left = new reobf.proghatches.gt.metatileentity.PatternDualInputHatch.DA(
                forcedHash(base(17139, 6139)),
                256);
            ICraftingPatternDetails right = new reobf.proghatches.gt.metatileentity.PatternDualInputHatch.DA(
                forcedHash(base(17141, 6141)),
                256);
            check(left.hashCode() == right.hashCode() && !left.equals(right), "programmable collision fixture failed");
            CraftingCPUCluster cpu = AESelfRecursiveCraftingSmoke.newCPU(null);
            cpu.addCrafting(left, 7);
            cpu.addCrafting(right, 11);
            check(
                count(cpu, left).equals(BigInteger.valueOf(7)) && count(cpu, right).equals(BigInteger.valueOf(11)),
                "programmable multiplied tasks were merged");
            Apeiron.LOG.info("Programmable Hatches DA multiplier collision separation passed");
        }

        private static ICraftingPatternDetails forcedHash(ICraftingPatternDetails original) {
            return (ICraftingPatternDetails) Proxy.newProxyInstance(
                ICraftingPatternDetails.class.getClassLoader(),
                new Class<?>[] { ICraftingPatternDetails.class },
                (proxy, method, args) -> {
                    if (method.getName()
                        .equals("hashCode")) return 42;
                    if (method.getName()
                        .equals("equals")) return proxy == args[0];
                    return method.invoke(original, args);
                });
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
