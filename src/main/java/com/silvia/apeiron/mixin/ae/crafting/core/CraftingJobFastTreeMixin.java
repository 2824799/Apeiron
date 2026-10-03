package com.silvia.apeiron.mixin.ae.crafting.core;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.silvia.apeiron.ae.crafting.core.CraftingTreeSource;
import com.silvia.apeiron.crafting.BigCraftingTree;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.crafting.fast.CraftingJobFast;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingRequest;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongMap;

/** Supplies the tree omitted by AE2's native fast planner too. */
@Mixin(value = CraftingJobFast.class, remap = false)
public abstract class CraftingJobFastTreeMixin implements CraftingTreeSource {

    @Shadow
    @Final
    private CraftingContext context;

    @Shadow
    @Final
    private CraftingRequest originalRequest;

    @Shadow
    @Final
    private AbstractObject2LongMap<ICraftingPatternDetails> tasks;

    @Shadow
    @Final
    private AbstractObject2LongMap<IAEStack<?>> ingredients;

    @Shadow
    private long byteCost;

    @Shadow
    private String errorMsg;

    @Override
    public BigCraftingTree getJobTree() {
        final Map<IAEStack<?>, ICraftingPatternDetails> patterns = new HashMap<>();
        final Map<ICraftingPatternDetails, BigInteger> counts = new HashMap<>();
        final Map<IAEStack<?>, BigInteger> stored = new HashMap<>();
        tasks.forEach((pattern, count) -> {
            counts.put(pattern, BigInteger.valueOf(count));
            for (IAEStack<?> output : pattern.getCondensedAEOutputs()) patterns.putIfAbsent(output, pattern);
        });
        ingredients.forEach((stack, count) -> stored.put(stack, BigInteger.valueOf(count)));
        return BigCraftingTree.create(
            context,
            originalRequest.stack,
            originalRequest.craftingMode,
            BigInteger.valueOf(byteCost),
            errorMsg,
            patterns,
            counts,
            stored);
    }
}
