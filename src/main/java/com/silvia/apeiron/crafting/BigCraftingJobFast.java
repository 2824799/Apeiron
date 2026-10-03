package com.silvia.apeiron.crafting;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import net.minecraft.world.World;

import com.silvia.apeiron.ae.crafting.core.BigCraftingCPU;
import com.silvia.apeiron.ae.crafting.core.BigCraftingJob;
import com.silvia.apeiron.ae.crafting.core.BigMECraftingInventory;
import com.silvia.apeiron.ae.crafting.core.BigSccResult;
import com.silvia.apeiron.ae.crafting.core.CraftingTreeSource;
import com.silvia.apeiron.ae.stack.BigAEItemStack;
import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.api.config.Actionable;
import appeng.api.config.CraftingMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.fast.SccResolver;
import appeng.crafting.v2.CraftingContext;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import it.unimi.dsi.fastutil.longs.LongObjectPair;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongMap;
import it.unimi.dsi.fastutil.objects.AbstractObject2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

/** Exact-count version of AE2's fast crafting calculator for large item requests. */
@SuppressWarnings({ "rawtypes", "unchecked" })
public final class BigCraftingJobFast<StackType extends IAEStack<StackType>>
    implements ICraftingJob<StackType>, BigCraftingJob, CraftingTreeSource {

    private final CraftingContext context;
    private final StackType output;
    private final CraftingMode craftingMode;
    private final ICraftingCallback callback;
    private final Map<ICraftingPatternDetails, BigInteger> tasks = new Object2ObjectOpenHashMap<>();
    private final Map<IAEStack<?>, BigInteger> ingredients = new Object2ObjectOpenHashMap<>();
    private final Map<IAEStack<?>, BigInteger> missingIngredients = new Object2ObjectOpenHashMap<>();
    private final Map<IAEStack<?>, ICraftingPatternDetails> treePatterns = new Object2ObjectOpenHashMap<>();
    private boolean calculated;
    private boolean simulated;
    private BigInteger byteCost = BigInteger.ZERO;
    private String errorMessage = "";

    public BigCraftingJobFast(final World world, final IGrid grid, final BaseActionSource source,
        final StackType output, final CraftingMode craftingMode, final ICraftingCallback callback) {
        this.context = new CraftingContext(world, grid, source);
        this.output = output;
        this.craftingMode = craftingMode;
        this.callback = callback;
        this.context.itemModel.ignore(output);
    }

    public CraftingContext getContext() {
        return context;
    }

    public void forEachPatternBig(java.util.function.BiConsumer<ICraftingPatternDetails, BigInteger> consumer) {
        calculate();
        tasks.forEach(consumer);
    }

    @Override
    public BigCraftingTree getJobTree() {
        calculate();
        return BigCraftingTree
            .create(context, output, craftingMode, byteCost, errorMessage, treePatterns, tasks, ingredients);
    }

    private void calculate() {
        if (this.calculated) return;
        try {
            calculateImpl();
        } catch (Throwable t) {
            this.errorMessage = t.toString();
            this.simulated = true;
        } finally {
            this.calculated = true;
            if (this.callback != null) this.callback.calculationComplete(this);
        }
    }

    private void calculateImpl() {
        final SccResolver.Result raw = SccResolver.compute(this.output, stack -> {
            final List<ICraftingPatternDetails> patterns = this.context.getPrecisePatternsFor(stack);
            if (patterns.isEmpty()) return null;
            for (final ICraftingPatternDetails pattern : patterns) {
                for (final IAEStack<?> candidate : pattern.getCondensedAEOutputs()) {
                    if (candidate.equals(stack)) return new it.unimi.dsi.fastutil.longs.LongObjectImmutablePair<>(
                        candidate.getStackSize(),
                        pattern);
                }
            }
            return null;
        });
        final BigSccResult result = (BigSccResult) (Object) raw;
        final AbstractObject2ObjectMap<IAEStack<?>, LongObjectPair<ICraftingPatternDetails>> patterns = result
            .getPatternsBig();
        final AbstractObject2LongMap<IAEStack<?>> inDegree = result.getInDegreeBig();
        final Set<IAEStack<?>> looping = result.getLoopingPatternsBig();
        final Set<IAEStack<?>> traversed = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        final Queue<IAEStack<?>> loopCandidates = new ArrayDeque<>();
        final Queue<IAEStack<?>> toTraverse = new ArrayDeque<>();

        final BigInteger requested = BigAEStackValues.get(this.output);
        if (requested.signum() < 0) throw new IllegalArgumentException("Request is negative");
        addCount(this.missingIngredients, this.output, requested);
        toTraverse.add(this.output);

        while (!toTraverse.isEmpty() || !loopCandidates.isEmpty()) {
            IAEStack<?> current = toTraverse.poll();
            if (current == null) current = loopCandidates.poll();
            if (current == null || !traversed.add(current)) continue;

            final LongObjectPair<ICraftingPatternDetails> pair = patterns.get(current);
            if (pair != null) {
                for (final IAEStack<?> input : pair.right()
                    .getCondensedAEInputs()) {
                    if (addLegacyCount(inDegree, input, -1L) == 0) toTraverse.add(input);
                    if (looping.contains(input)) loopCandidates.add(input);
                }
            }
            exploreItem(current, pair);
        }

        for (final IAEStack<?> stack : new ArrayList<>(this.missingIngredients.keySet())) {
            moveMissingByExtract(stack);
        }
    }

    private void exploreItem(final IAEStack<?> current, final LongObjectPair<ICraftingPatternDetails> pair) {
        final BigInteger count = moveMissingByExtract(current);
        if (count.signum() == 0 || pair == null) return;
        final BigInteger outputPerPattern = outputPerPattern(current, pair);
        if (outputPerPattern.signum() <= 0) throw new IllegalStateException("Pattern has no output");
        final BigInteger multiplier = ceilDiv(count, outputPerPattern);
        for (final IAEStack<?> input : pair.right()
            .getCondensedAEInputs()) {
            final BigInteger inputAmount = BigAEStackValues.get(input);
            if (inputAmount.signum() < 0) throw new IllegalStateException("Pattern has negative inputs");
            addCount(this.missingIngredients, input, inputAmount.multiply(multiplier));
        }
        addCount(this.tasks, pair.right(), multiplier);
        this.treePatterns.put(current, pair.right());
        addByteCost(current, multiplier.multiply(outputPerPattern));
        addCount(this.missingIngredients, current, count.negate());
    }

    private static BigInteger outputPerPattern(final IAEStack<?> current,
        final LongObjectPair<ICraftingPatternDetails> pair) {
        for (final IAEStack<?> output : pair.right()
            .getCondensedAEOutputs()) {
            if (output.equals(current)) return BigAEStackValues.get(output);
        }
        return BigInteger.valueOf(pair.leftLong());
    }

    private BigInteger moveMissingByExtract(final IAEStack<?> stack) {
        final BigInteger count = this.missingIngredients.getOrDefault(stack, BigInteger.ZERO);
        if (count.signum() <= 0) return BigInteger.ZERO;
        final IAEStack<?> request = stack.copy();
        BigAEStackValues.set(request, count);
        final IAEStack<?> result = ((BigMECraftingInventory) this.context.itemModel)
            .extractItemsBig(request, Actionable.MODULATE);
        if (result == null) return count;
        final BigInteger extracted = BigAEStackValues.get(result);
        if (extracted.signum() <= 0) return count;
        addCount(this.ingredients, stack, extracted);
        addByteCost(stack, extracted);
        if (extracted.compareTo(count) >= 0) {
            this.missingIngredients.remove(stack);
            return BigInteger.ZERO;
        }
        addCount(this.missingIngredients, stack, extracted.negate());
        return count.subtract(extracted);
    }

    private void addByteCost(final IAEStack<?> stack, final BigInteger amount) {
        final int amountPerUnit = Math.max(1, stack.getAmountPerUnit());
        final BigInteger delta = ceilDiv(amount, BigInteger.valueOf(amountPerUnit));
        this.byteCost = this.byteCost.add(delta);
        if (this.byteCost.signum() < 0) this.byteCost = BigInteger.valueOf(Long.MAX_VALUE);
    }

    private static <T> void addCount(final Map<T, BigInteger> map, final T key, final BigInteger delta) {
        final BigInteger result = map.getOrDefault(key, BigInteger.ZERO)
            .add(delta);
        if (result.signum() < 0) throw new IllegalStateException("Negative crafting count for " + key);
        if (result.signum() == 0) map.remove(key);
        else map.put(key, result);
    }

    private static long addLegacyCount(final AbstractObject2LongMap<IAEStack<?>> map, final IAEStack<?> key,
        final long delta) {
        final long oldValue = map.getLong(key);
        final long newValue = Math.addExact(oldValue, delta);
        if (newValue == 0) map.removeLong(key);
        else map.put(key, newValue);
        return newValue;
    }

    private static BigInteger ceilDiv(final BigInteger value, final BigInteger divisor) {
        return value.add(divisor)
            .subtract(BigInteger.ONE)
            .divide(divisor);
    }

    @Override
    public boolean isSimulation() {
        calculate();
        return this.simulated || this.craftingMode != CraftingMode.IGNORE_MISSING && !this.missingIngredients.isEmpty();
    }

    @Override
    public long getByteTotal() {
        calculate();
        return BigAEStackValues.saturatedLong(this.byteCost);
    }

    @Override
    public BigInteger getByteTotalBig() {
        calculate();
        return this.byteCost;
    }

    @Override
    public void populatePlan(final IItemList<IAEStack<?>> plan) {
        calculate();
        for (final Map.Entry<ICraftingPatternDetails, BigInteger> entry : this.tasks.entrySet()) {
            for (final IAEStack<?> output : entry.getKey()
                .getCondensedAEOutputs()) {
                final IAEStack<?> copy = output.copy();
                final BigInteger crafts = entry.getValue();
                final BigInteger total = BigAEStackValues.get(output)
                    .multiply(crafts);
                BigAEStackValues.set(copy, BigInteger.ZERO);
                if (copy instanceof BigAEItemStack) {
                    final BigAEItemStack exact = (BigAEItemStack) copy;
                    exact.setCountRequestableBig(total);
                    exact.setCountRequestableCraftsBig(crafts);
                } else {
                    copy.setCountRequestable(BigAEStackValues.saturatedLong(total));
                    copy.setCountRequestableCrafts(BigAEStackValues.saturatedLong(crafts));
                }
                ((IItemList) plan).addRequestable(copy);
            }
        }
        for (final Map.Entry<IAEStack<?>, BigInteger> entry : this.ingredients.entrySet()) {
            final IAEStack<?> copy = entry.getKey()
                .copy();
            BigAEStackValues.set(copy, entry.getValue());
            plan.add(copy);
        }
        for (final Map.Entry<IAEStack<?>, BigInteger> entry : this.missingIngredients.entrySet()) {
            final IAEStack<?> copy = entry.getKey()
                .copy();
            if (this.craftingMode == CraftingMode.IGNORE_MISSING) {
                BigAEStackValues.set(copy, BigInteger.ZERO);
                if (copy instanceof BigAEItemStack) ((BigAEItemStack) copy).setCountRequestableBig(entry.getValue());
                else copy.setCountRequestable(BigAEStackValues.saturatedLong(entry.getValue()));
                ((IItemList) plan).addRequestable(copy);
            } else {
                BigAEStackValues.set(copy, entry.getValue());
                plan.add(copy);
            }
        }
    }

    @Override
    public StackType getOutput() {
        return this.output;
    }

    @Override
    public boolean simulateFor(final int milli) {
        calculate();
        return false;
    }

    @Override
    public Future<ICraftingJob<StackType>> schedule() {
        calculate();
        return CompletableFuture.completedFuture(this);
    }

    @Override
    public boolean supportsCPUCluster(final ICraftingCPU cluster) {
        return cluster instanceof CraftingCPUCluster && cluster instanceof BigCraftingCPU;
    }

    @Override
    public CraftingMode getCraftingMode() {
        return this.craftingMode;
    }

    @Override
    public void startCrafting(final MECraftingInventory storage, final ICraftingCPU rawCluster,
        final BaseActionSource source) {
        calculate();
        final BigCraftingCPU cluster = (BigCraftingCPU) rawCluster;
        for (final Map.Entry<ICraftingPatternDetails, BigInteger> entry : this.tasks.entrySet()) {
            cluster.addCraftingBig(entry.getKey(), entry.getValue());
        }
        for (final Map.Entry<IAEStack<?>, BigInteger> entry : this.ingredients.entrySet()) {
            pullStack(storage, (CraftingCPUCluster) rawCluster, entry.getKey(), entry.getValue());
        }
        for (final Map.Entry<IAEStack<?>, BigInteger> entry : this.missingIngredients.entrySet()) {
            pullStack(storage, (CraftingCPUCluster) rawCluster, entry.getKey(), entry.getValue());
        }
    }

    private void pullStack(final MECraftingInventory storage, final CraftingCPUCluster cluster, final IAEStack<?> stack,
        final BigInteger count) {
        final IAEStack<?> request = stack.copy();
        BigAEStackValues.set(request, count);
        final IAEStack<?> extracted = ((BigMECraftingInventory) storage).extractItemsBig(request, Actionable.MODULATE);
        final BigInteger actual = extracted == null ? BigInteger.ZERO : BigAEStackValues.get(extracted);
        if (actual.compareTo(count) != 0) {
            if (cluster.isMissingMode()) {
                final IAEStack<?> missing = stack.copy();
                BigAEStackValues.set(missing, count.subtract(actual));
                cluster.addEmitable(missing);
            } else {
                throw new CraftBranchFailure(stack, BigAEStackValues.saturatedLong(count.subtract(actual)));
            }
        }
        if (extracted != null && actual.signum() > 0) cluster.addStorage(extracted);
    }

    @Override
    public MECraftingInventory getStorageAtBeginning() {
        return this.context.availableCache;
    }

    @Override
    public boolean supportsOptimization() {
        return true;
    }

    @Override
    public String getErrorMessage() {
        return this.errorMessage;
    }
}
