package com.silvia.apeiron.common.machine.me.input.pattern;

import java.math.BigInteger;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;

/** One detached insertion probe and its matches; quick move validates the same item against many slots. */
public final class PatternInsertionCache {

    private ItemStack snapshot;
    private World world;
    private ICraftingPatternDetails decoded;
    private final Map<BigInteger, BitSet> matches = new HashMap<>();

    public BitSet matchingSlots(ItemStack stack, World currentWorld, BigInteger multiplier, int slotCount,
        IntFunction<ICraftingPatternDetails> existing) {
        if (!isCurrent(stack, currentWorld)) {
            snapshot = stack.copy();
            world = currentWorld;
            // Decoders may annotate broken patterns. Neither the player stack nor the cache key may be mutated.
            decoded = decode(snapshot, currentWorld);
            invalidateMatches();
        }
        if (decoded == null) return null;
        return matches.computeIfAbsent(multiplier, value -> {
            ICraftingPatternDetails candidate = value.equals(BigInteger.ONE) ? decoded
                : new MultipliedPatternDetails(decoded, value);
            BitSet result = new BitSet(slotCount);
            for (int slot = 0; slot < slotCount; slot++)
                if (PatternRecipeComparison.sameRecipe(candidate, existing.apply(slot))) result.set(slot);
            return result;
        });
    }

    /** Promote an already validated item when it becomes a stored pattern, including a cached invalid result. */
    public ICraftingPatternDetails decodeStored(ItemStack stack, World currentWorld) {
        return isCurrent(stack, currentWorld) ? decoded : decode(stack, currentWorld);
    }

    private boolean isCurrent(ItemStack stack, World currentWorld) {
        return snapshot != null && world == currentWorld && ItemStack.areItemStacksEqual(snapshot, stack);
    }

    private static ICraftingPatternDetails decode(ItemStack stack, World world) {
        return stack != null && stack.getItem() instanceof ICraftingPatternItem item
            ? item.getPatternForItem(stack.copy(), world)
            : null;
    }

    public void invalidateMatches() {
        matches.clear();
    }

    public void clear() {
        snapshot = null;
        world = null;
        decoded = null;
        invalidateMatches();
    }
}
