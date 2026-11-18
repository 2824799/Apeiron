package com.silvia.apeiron.crafting;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.config.ApeironConfig;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;

/** Shared exact arithmetic for patterns that consume and produce their requested stack. */
public final class CraftingPatternMath {

    private CraftingPatternMath() {}

    public static BigInteger outputAmount(final ICraftingPatternDetails pattern, final IAEStack<?> requested) {
        BigInteger amount = BigInteger.ZERO;
        for (final IAEStack<?> output : pattern.getCondensedAEOutputs()) {
            if (output.equals(requested)) amount = amount.add(BigAEStackValues.get(output));
        }
        return amount;
    }

    public static BigInteger recursiveInputAmount(final IAEStack<?>[] inputs, final IAEStack<?> requested) {
        BigInteger amount = BigInteger.ZERO;
        for (final IAEStack<?> input : inputs) {
            if (input.equals(requested)) amount = amount.add(BigAEStackValues.get(input));
        }
        return amount;
    }

    public static boolean isPositiveSelfRecursive(final BigInteger output, final BigInteger recursiveInput) {
        return ApeironConfig.isAeSelfRecursiveCraftingEnabled() && recursiveInput.signum() > 0
            && output.compareTo(recursiveInput) > 0;
    }

    /** Returns the inputs that must come from outside a profitable self-recursive pattern. */
    public static IAEStack<?>[] externalInputs(final IAEStack<?>[] source, final IAEStack<?> requested) {
        final List<IAEStack<?>> inputs = new ArrayList<>();
        for (final IAEStack<?> input : source) {
            if (!input.equals(requested)) inputs.add(input);
        }
        return inputs.toArray(new IAEStack<?>[0]);
    }
}
