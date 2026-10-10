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

    public static BigInteger recursiveInputAmount(final ICraftingPatternDetails pattern, final IAEStack<?> requested) {
        BigInteger amount = BigInteger.ZERO;
        for (final IAEStack<?> input : pattern.getCondensedAEInputs()) {
            if (input.equals(requested)) amount = amount.add(BigAEStackValues.get(input));
        }
        return amount;
    }

    public static BigInteger netOutputAmount(final ICraftingPatternDetails pattern, final IAEStack<?> requested) {
        final BigInteger output = outputAmount(pattern, requested);
        final BigInteger recursiveInput = recursiveInputAmount(pattern, requested);
        if (!ApeironConfig.isAeSelfRecursiveCraftingEnabled() || recursiveInput.signum() <= 0) return output;
        return output.subtract(recursiveInput);
    }

    public static boolean isPositiveSelfRecursive(final ICraftingPatternDetails pattern, final IAEStack<?> requested) {
        return ApeironConfig.isAeSelfRecursiveCraftingEnabled() && recursiveInputAmount(pattern, requested).signum() > 0
            && netOutputAmount(pattern, requested).signum() > 0;
    }

    /** Returns the inputs that must come from outside a profitable self-recursive pattern. */
    public static IAEStack<?>[] externalInputs(final ICraftingPatternDetails pattern, final IAEStack<?> requested) {
        if (!isPositiveSelfRecursive(pattern, requested)) return pattern.getCondensedAEInputs();
        final List<IAEStack<?>> inputs = new ArrayList<>();
        for (final IAEStack<?> input : pattern.getCondensedAEInputs()) {
            if (!input.equals(requested)) inputs.add(input);
        }
        return inputs.toArray(new IAEStack<?>[0]);
    }
}
