package com.silvia.apeiron.crafting;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.World;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.config.CraftingMode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.core.localization.GuiText;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingJobV2;
import appeng.crafting.v2.CraftingRequest;
import appeng.crafting.v2.CraftingRequest.SubstitutionMode;
import appeng.crafting.v2.CraftingRequest.UsedResolverEntry;
import appeng.crafting.v2.CraftingTreeSerializer;
import appeng.crafting.v2.ITreeSerializable;
import appeng.crafting.v2.resolvers.CraftableItemResolver.CraftFromPatternTask;
import appeng.crafting.v2.resolvers.CraftableItemResolver.RequestAndPerCraftAmount;
import appeng.crafting.v2.resolvers.CraftingTask;
import appeng.crafting.v2.resolvers.ExtractItemResolver.ExtractItemTask;
import appeng.crafting.v2.resolvers.IgnoreMissingItemResolver.IgnoreMissingItemTask;
import appeng.crafting.v2.resolvers.SimulateMissingItemResolver.ConjureItemTask;
import appeng.util.Platform;
import cpw.mods.fml.common.network.ByteBufUtils;

/** A display snapshot of the exact plan, transported and rendered by AE2's native crafting tree. */
@SuppressWarnings({ "rawtypes", "unchecked" })
public final class BigCraftingTree extends CraftingJobV2 {

    private BigInteger exactBytes;

    public static void register() {
        CraftingTreeSerializer.registerSerializable("apeiron:tree", BigCraftingTree.class);
        CraftingTreeSerializer.registerSerializable("apeiron:pattern", PatternTask.class);
    }

    public BigCraftingTree(CraftingTreeSerializer serializer, ITreeSerializable parent) throws IOException {
        super(serializer, parent);
        exactBytes = BigValueCodec.readPacket(serializer.getBuffer());
    }

    private static CraftingTreeSerializer seed(World world, CraftingRequest request, BigInteger bytes, String error)
        throws IOException {
        // Seed only the native header; creating another CraftingContext would copy the whole ME inventory again.
        final CraftingTreeSerializer writer = new CraftingTreeSerializer(world);
        writer.getBuffer()
            .writeLong(BigAEStackValues.saturatedLong(bytes));
        writer.writeEnum(State.FINISHED);
        ByteBufUtils.writeUTF8String(writer.getBuffer(), error);
        request.serializeTree(writer);
        BigValueCodec.writePacket(writer.getBuffer(), bytes);
        final CraftingTreeSerializer reader = new CraftingTreeSerializer(
            world,
            com.silvia.apeiron.compat.CraftingTreeCodecs.finish(writer));
        com.silvia.apeiron.compat.CraftingTreeCodecs.initialize(reader);
        return reader;
    }

    @Override
    public List<? extends ITreeSerializable> serializeTree(CraftingTreeSerializer serializer) throws IOException {
        final List<? extends ITreeSerializable> children = super.serializeTree(serializer);
        BigValueCodec.writePacket(serializer.getBuffer(), exactBytes);
        return children;
    }

    public BigInteger getExactBytes() {
        return exactBytes;
    }

    public static BigCraftingTree create(CraftingContext context, IAEStack<?> output, CraftingMode mode,
        BigInteger bytes, String error, Map<IAEStack<?>, ICraftingPatternDetails> patterns,
        Map<ICraftingPatternDetails, BigInteger> plannedCrafts, Map<IAEStack<?>, BigInteger> ingredients) {
        final CraftingRequest root = request(output, BigAEStackValues.get(output), mode);
        final Map<IAEStack<?>, BigInteger> stored = new HashMap<>(ingredients);
        final Map<IAEStack<?>, BigInteger> byproducts = new HashMap<>();
        final Map<ICraftingPatternDetails, BigInteger> crafts = new HashMap<>(plannedCrafts);
        final ArrayDeque<Branch> pending = new ArrayDeque<>();
        pending.push(new Branch(root, Collections.emptySet()));
        int nodes = 0;
        while (!pending.isEmpty()) {
            final Branch branch = pending.pop();
            final CraftingRequest current = branch.request;
            // Bound visual expansion independently of the number of requested items, including cyclic patterns.
            if (++nodes > 16384 || branch.parents.contains(current.stack)) {
                current.incomplete = true;
                continue;
            }
            final ICraftingPatternDetails pattern = patterns.get(current.stack);
            final boolean selfRecursive = pattern != null
                && CraftingPatternMath.isPositiveSelfRecursive(pattern, current.stack);
            BigInteger remaining = BigAEStackValues.get(current.stack);
            if (selfRecursive) {
                extract(current, stored, CraftingPatternMath.recursiveInputAmount(pattern, current.stack), false);
            } else {
                remaining = extract(current, stored, remaining, false);
            }
            remaining = extract(current, byproducts, remaining, true);
            if (remaining.signum() > 0 && pattern != null) {
                final BigInteger perCraft = selfRecursive ? CraftingPatternMath.netOutputAmount(pattern, current.stack)
                    : outputAmount(pattern, current.stack);
                final BigInteger count = remaining.add(perCraft)
                    .subtract(BigInteger.ONE)
                    .divide(perCraft)
                    .min(crafts.getOrDefault(pattern, BigInteger.ZERO));
                if (count.signum() > 0) {
                    crafts.put(
                        pattern,
                        crafts.get(pattern)
                            .subtract(count));
                    for (IAEStack<?> produced : pattern.getCondensedAEOutputs()) {
                        byproducts.merge(
                            produced,
                            BigAEStackValues.get(produced)
                                .multiply(count),
                            BigInteger::add);
                    }
                    final BigInteger resolved = take(byproducts, current.stack, remaining);
                    final PatternTask task = new PatternTask(current, pattern, count);
                    task.craftingMachine = context.getCrafterIconForPattern(pattern);
                    current.usedResolvers.add(new UsedResolverEntry(current, task, stack(current.stack, resolved)));
                    remaining = remaining.subtract(resolved);
                    final Set<IAEStack<?>> parents = new HashSet<>(branch.parents);
                    parents.add(current.stack);
                    final IAEStack<?>[] inputs = CraftingPatternMath.externalInputs(pattern, current.stack);
                    for (int i = inputs.length - 1; i >= 0; i--) {
                        final IAEStack<?> input = inputs[i];
                        final CraftingRequest child = request(
                            input,
                            BigAEStackValues.get(input)
                                .multiply(count),
                            mode);
                        task.prependChild(child, input.getStackSize());
                        pending.push(new Branch(child, parents));
                    }
                }
            }
            if (remaining.signum() > 0) {
                current.wasSimulated = true;
                final CraftingTask missing = mode == CraftingMode.IGNORE_MISSING ? new IgnoreMissingItemTask(current)
                    : new ConjureItemTask(current);
                current.usedResolvers.add(new UsedResolverEntry(current, missing, stack(current.stack, remaining)));
            }
            current.remainingToProcess = 0;
        }
        try {
            final CraftingTreeSerializer header = seed(context.world, root, bytes, error);
            try {
                final BigCraftingTree tree = new BigCraftingTree(header, null);
                tree.context = context;
                tree.originalRequest = root;
                return tree;
            } finally {
                header.getBuffer()
                    .release();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not build exact crafting tree", e);
        }
    }

    private static BigInteger outputAmount(ICraftingPatternDetails pattern, IAEStack<?> item) {
        for (IAEStack<?> output : pattern.getCondensedAEOutputs()) {
            if (output.equals(item)) return BigAEStackValues.get(output);
        }
        throw new IllegalStateException("Tree pattern does not produce the requested stack");
    }

    private static CraftingRequest request(IAEStack<?> type, BigInteger amount, CraftingMode mode) {
        return new CraftingRequest(stack(type, amount), SubstitutionMode.PRECISE, true, mode);
    }

    private static IAEStack<?> stack(IAEStack<?> type, BigInteger amount) {
        return BigAEStackValues.copyWithSize(type, amount);
    }

    private static BigInteger take(Map<IAEStack<?>, BigInteger> source, IAEStack<?> type, BigInteger requested) {
        final BigInteger available = source.getOrDefault(type, BigInteger.ZERO);
        final BigInteger amount = requested.min(available);
        source.put(type, available.subtract(amount));
        return amount;
    }

    private static BigInteger extract(CraftingRequest request, Map<IAEStack<?>, BigInteger> source,
        BigInteger remaining, boolean byproduct) {
        final BigInteger amount = take(source, request.stack, remaining);
        if (amount.signum() > 0) {
            final ExtractItemTask task = new ExtractItemTask(request);
            final IAEStack<?> resolved = stack(request.stack, amount);
            (byproduct ? task.removedFromByproducts : task.removedFromSystem).add(resolved);
            request.usedResolvers.add(new UsedResolverEntry(request, task, resolved));
        }
        return remaining.subtract(amount);
    }

    private static final class Branch {

        final CraftingRequest request;
        final Set<IAEStack<?>> parents;

        Branch(CraftingRequest request, Set<IAEStack<?>> parents) {
            this.request = request;
            this.parents = parents;
        }
    }

    public static final class PatternTask extends CraftFromPatternTask {

        private final BigInteger exactCrafts;

        PatternTask(CraftingRequest request, ICraftingPatternDetails pattern, BigInteger crafts) {
            super(request, pattern, PRIORITY_CRAFT_OFFSET, true, false);
            exactCrafts = crafts;
            totalCraftsDone = BigAEStackValues.saturatedLong(crafts);
            state = CraftingTask.State.SUCCESS;
        }

        public PatternTask(CraftingTreeSerializer serializer, ITreeSerializable parent) throws IOException {
            super(serializer, parent);
            exactCrafts = BigValueCodec.readPacket(serializer.getBuffer());
        }

        @Override
        public List<? extends ITreeSerializable> serializeTree(CraftingTreeSerializer serializer) throws IOException {
            final List<? extends ITreeSerializable> children = super.serializeTree(serializer);
            BigValueCodec.writePacket(serializer.getBuffer(), exactCrafts);
            return children;
        }

        public BigInteger getExactCrafts() {
            return exactCrafts;
        }

        @Override
        public String getTooltipText() {
            return GuiText.Crafting.getLocal() + "\n "
                + GuiText.Crafts.getLocal()
                + ": "
                + exactCrafts
                + "\n "
                + GuiText.Interface.getLocal()
                + ": "
                + Platform.getItemDisplayName(craftingMachine);
        }

        private void prependChild(CraftingRequest request, long perCraft) {
            childRequests.add(0, new RequestAndPerCraftAmount(request, perCraft));
        }
    }
}
