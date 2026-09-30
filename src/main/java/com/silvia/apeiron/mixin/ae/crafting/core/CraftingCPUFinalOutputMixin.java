package com.silvia.apeiron.mixin.ae.crafting.core;

import java.math.BigInteger;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.crafting.core.BigCraftingCPU;
import com.silvia.apeiron.ae.crafting.core.BigFinalOutput;
import com.silvia.apeiron.ae.crafting.core.BigTaskProgress;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;

/** Keeps final-output reservation and multiplication exact. */
@Mixin(value = CraftingCPUCluster.finalOutput.class, remap = false)
public abstract class CraftingCPUFinalOutputMixin implements BigFinalOutput {

    @Shadow
    IAEStack originalOutput;

    @Shadow
    IAEStack<?>[] patternOutputs;

    @Shadow
    IItemList<IAEStack<?>> outputs;

    @Shadow
    @Final
    CraftingCPUCluster this$0;

    @Override
    public BigInteger getOriginalCountBig() {
        return this.originalOutput == null ? BigInteger.ZERO : BigAEStackValues.get(this.originalOutput);
    }

    @Overwrite
    protected void addOutputs(final IAEStack<?> output) {
        ICraftingPatternDetails details = null;
        BigInteger multiplier = BigInteger.ZERO;
        final BigCraftingCPU cpu = (BigCraftingCPU) this.this$0;

        outer: for (final Map.Entry<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> entry : cpu
                .getTaskEntriesBig().entrySet()) {
            for (final IAEStack<?> candidate : entry.getKey().getCondensedAEOutputs()) {
                if (candidate.equals(output)) {
                    details = entry.getKey();
                    final BigInteger denominator = BigAEStackValues.get(candidate);
                    final BigInteger numerator = BigAEStackValues.get(output);
                    multiplier = denominator.signum() <= 0 ? BigInteger.ZERO
                            : numerator.add(denominator).subtract(BigInteger.ONE).divide(denominator);
                    break outer;
                }
            }
        }

        if (details == null) {
            this.outputs.add(output.copy().setCraftable(false).setCountRequestable(0));
            return;
        }

        this.patternOutputs = details.getCondensedAEOutputs().clone();
        for (final IAEStack<?> stack : this.patternOutputs) {
            final IAEStack<?> copy = stack.copy();
            BigAEStackValues.set(copy, BigAEStackValues.get(copy).multiply(multiplier));
            this.outputs.add(copy);
        }
    }

    @Overwrite
    protected long getRemainingIngredientAmount(final IAEStack<?> output) {
        return BigAEStackValues.saturatedLong(getRemainingIngredientAmountBig(output));
    }

    @Override
    public BigInteger getRemainingIngredientAmountBig(final IAEStack<?> output) {
        final BigCraftingCPU cpu = (BigCraftingCPU) this.this$0;
        BigInteger required = BigInteger.ZERO;
        for (final Map.Entry<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> entry : cpu
                .getTaskEntriesBig().entrySet()) {
            if (((BigTaskProgress) entry.getValue()).getValueBig().signum() <= 0) continue;
            for (final IAEStack<?> input : entry.getKey().getCondensedAEInputs()) {
                if (input.equals(output)) {
                    required = required.add(
                            BigAEStackValues.get(input)
                                    .multiply(((BigTaskProgress) entry.getValue()).getValueBig()));
                }
            }
        }

        final MECraftingInventory inventory = this.this$0.getInventory();
        @SuppressWarnings("rawtypes")
        final IAEStack available = (IAEStack) inventory.findPrecise((IAEStack) output);
        if (available != null) required = required.subtract(BigAEStackValues.get(available));
        if (required.signum() < 0) required = BigInteger.ZERO;
        return required.min(BigAEStackValues.get(output));
    }

    @Override
    public IAEStack<?> splitOutputToIngredientBig(final IAEStack<?> output, final Actionable mode) {
        return this.splitOutputToIngredient(output, mode);
    }

    @Overwrite
    protected IAEStack<?> splitOutputToIngredient(final IAEStack<?> output, final Actionable type) {
        final BigInteger ingredientAmount = getRemainingIngredientAmountBig(output);
        if (ingredientAmount.signum() <= 0) return output;

        final IAEStack<?> ingredientOutput = output.copy();
        BigAEStackValues.set(ingredientOutput, ingredientAmount);
        if (type == Actionable.MODULATE) this.this$0.getInventory().injectItems(ingredientOutput, Actionable.MODULATE);

        final BigInteger outputAmount = BigAEStackValues.get(output);
        if (ingredientAmount.compareTo(outputAmount) >= 0) return null;
        final IAEStack<?> remaining = output.copy();
        BigAEStackValues.set(remaining, outputAmount.subtract(ingredientAmount));
        return remaining;
    }
}
