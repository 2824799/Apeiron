package com.silvia.apeiron.common.machine.parallel;

import java.math.BigInteger;
import java.util.List;

import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.util.GTRecipe;

/** Resolve independent substitute-fluid slots using GT's best single alternative, with exact stock quantities. */
public final class RecipeFluidInputs {

    private RecipeFluidInputs() {}

    public static boolean supports(GTRecipe recipe) {
        if (recipe.mAltFluidInputs == null) return true;
        // Overlapping substitute slots use GT's sequential pool selection; retain that native policy.
        for (int i = 0; i < recipe.mFluidInputs.length; i++) {
            if (!consumed(recipe.mFluidInputs[i])) continue;
            if (hasAlternatives(recipe, i) && alternatives(recipe, i).length > 0) {
                boolean valid = false;
                for (FluidStack candidate : alternatives(recipe, i)) valid |= consumed(candidate);
                if (!valid) return false;
            }
            for (int j = i + 1; j < recipe.mFluidInputs.length; j++) {
                if (!consumed(recipe.mFluidInputs[j]) || !hasAlternatives(recipe, i) && !hasAlternatives(recipe, j))
                    continue;
                for (FluidStack first : alternatives(recipe, i)) for (FluidStack second : alternatives(recipe, j))
                    if (consumed(first) && consumed(second) && first.getFluid() == second.getFluid()) return false;
            }
        }
        return true;
    }

    public static FluidStack resolve(GTRecipe recipe, int slot, List<IAEStack<?>> stocks) {
        FluidStack representative = recipe.mFluidInputs[slot];
        if (!consumed(representative) || !hasAlternatives(recipe, slot)) return representative;
        if (alternatives(recipe, slot).length == 0) return null;
        FluidStack selected = null;
        FluidStack fallback = null;
        BigInteger best = BigInteger.ZERO;
        boolean bestInfinite = false;
        for (FluidStack candidate : alternatives(recipe, slot)) {
            if (!consumed(candidate)) continue;
            if (fallback == null) fallback = candidate;
            BigInteger available = BigInteger.ZERO;
            boolean infinite = false;
            for (IAEStack<?> stock : stocks)
                if (stock instanceof IAEFluidStack && ((IAEFluidStack) stock).getFluidStack()
                    .isFluidEqual(candidate)) {
                        available = available.add(BigAEStackValues.get(stock));
                        infinite |= BigAEStackValues.isInfinite(stock);
                    }
            if (!infinite && available.signum() == 0) continue;
            if (selected == null || infinite && !bestInfinite
                || infinite == bestInfinite && !infinite
                    && available.multiply(BigInteger.valueOf(selected.amount))
                        .compareTo(best.multiply(BigInteger.valueOf(candidate.amount))) > 0) {
                selected = candidate;
                best = available;
                bestInfinite = infinite;
            }
        }
        // A missing alternative must remain an unsatisfied requirement rather than remove the input cost.
        return selected == null ? fallback : selected;
    }

    private static boolean hasAlternatives(GTRecipe recipe, int slot) {
        return recipe.mAltFluidInputs != null && slot < recipe.mAltFluidInputs.length
            && recipe.mAltFluidInputs[slot] != null;
    }

    private static FluidStack[] alternatives(GTRecipe recipe, int slot) {
        return hasAlternatives(recipe, slot) ? recipe.mAltFluidInputs[slot]
            : new FluidStack[] { recipe.mFluidInputs[slot] };
    }

    private static boolean consumed(FluidStack fluid) {
        return fluid != null && fluid.amount > 0;
    }
}
