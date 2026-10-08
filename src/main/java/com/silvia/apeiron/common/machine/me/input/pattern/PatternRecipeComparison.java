package com.silvia.apeiron.common.machine.me.input.pattern;

import java.util.Objects;

import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;

/** Compare the executable recipe, retaining ingredient NBT but ignoring the encoded item's bookkeeping. */
public final class PatternRecipeComparison {

    private PatternRecipeComparison() {}

    public static boolean sameRecipe(ICraftingPatternDetails first, ICraftingPatternDetails second) {
        if (first == null || second == null
            || first.isCraftable() != second.isCraftable()
            || first.canSubstitute() != second.canSubstitute()
            || first.canBeSubstitute() != second.canBeSubstitute()
            || first.isInputOnly() != second.isInputOnly()
            || !Objects.equals(first.getInputOnlyUuid(), second.getInputOnlyUuid())) return false;
        if (first.isCraftable() && !sameLayout(first.getAEInputs(), second.getAEInputs())) return false;
        return sameContents(first.getCondensedAEInputs(), second.getCondensedAEInputs())
            && sameContents(first.getCondensedAEOutputs(), second.getCondensedAEOutputs())
            // AE treats the first output as the primary product; preserve that choice.
            && sameType(firstOutput(first.getAEOutputs()), firstOutput(second.getAEOutputs()));
    }

    private static IAEStack<?> firstOutput(IAEStack<?>[] outputs) {
        for (IAEStack<?> output : outputs) if (output != null) return output;
        return null;
    }

    private static boolean sameLayout(IAEStack<?>[] first, IAEStack<?>[] second) {
        int size = Math.max(first.length, second.length);
        for (int slot = 0; slot < size; slot++)
            if (!sameStack(slot < first.length ? first[slot] : null, slot < second.length ? second[slot] : null))
                return false;
        return true;
    }

    private static boolean sameContents(IAEStack<?>[] first, IAEStack<?>[] second) {
        if (first.length != second.length) return false;
        boolean[] matched = new boolean[second.length];
        for (IAEStack<?> stack : first) {
            boolean found = false;
            for (int slot = 0; slot < second.length; slot++) if (!matched[slot] && sameStack(stack, second[slot])) {
                matched[slot] = true;
                found = true;
                break;
            }
            if (!found) return false;
        }
        return true;
    }

    private static boolean sameStack(IAEStack<?> first, IAEStack<?> second) {
        return sameType(first, second) && (first == null || BigAEStackValues.get(first)
            .equals(BigAEStackValues.get(second)));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static boolean sameType(IAEStack<?> first, IAEStack<?> second) {
        if (first == null || second == null) return first == second;
        return first.getStackType() == second.getStackType() && ((IAEStack) first).isSameType(second);
    }
}
