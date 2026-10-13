package com.silvia.apeiron.mixin.gregtech.energy;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigRecipeInputProvider;
import com.silvia.apeiron.api.machine.parallel.BigRecipeOutputModifier;
import com.silvia.apeiron.common.machine.parallel.BigRecipeParallelHelper;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;
import gregtech.common.tileentities.machines.multi.MTESolarFactory;

/** Keeps the native wafer validation and recipe adjustment, with post-parallel bonuses in the exact output ledger. */
@Pseudo
@Mixin(targets = "gregtech.common.tileentities.machines.multi.MTESolarFactory$1", remap = false)
public abstract class SolarFactoryProcessingMixin extends ProcessingLogic
    implements BigRecipeInputProvider, BigRecipeOutputModifier {

    @Shadow
    @Final
    private MTESolarFactory this$0;
    @Shadow
    private int foundWaferTier;
    @Shadow
    private int minimumTierForRecipe;
    @Shadow
    private boolean shouldMultiplyOutputs;

    @Shadow
    private GTRecipe adjustRecipe(GTRecipe recipe) {
        throw new AssertionError();
    }

    @Shadow
    private void clearVars() {
        throw new AssertionError();
    }

    @Override
    public boolean hasExactRecipeInputs() {
        return true;
    }

    @Override
    public GTRecipe getExactInputRecipe(GTRecipe recipe) {
        return adjustRecipe(recipe);
    }

    @Override
    public UnaryOperator<List<IAEStack<?>>> snapshotOutputModifier(GTRecipe recipe) {
        if (!shouldMultiplyOutputs || recipe.mOutputs.length == 0 || recipe.mOutputs[0] == null)
            return UnaryOperator.identity();
        // The native tier-2/3 rule changes in quarters; four items recover its capped multiplier exactly.
        int quarters = ((SolarFactoryOutputAccessor) this$0).apeiron$nativeOutput(
            GTUtility.copyAmountUnsafe(4, recipe.mOutputs[0]),
            foundWaferTier - minimumTierForRecipe)[0].stackSize;
        return outputs -> {
            List<IAEStack<?>> adjusted = new ArrayList<>();
            boolean firstItem = true;
            for (IAEStack<?> output : outputs) {
                if (output instanceof IAEItemStack) {
                    if (firstItem) adjusted.add(
                        BigAEStackValues.copyWithSize(
                            output,
                            BigAEStackValues.get(output)
                                .multiply(BigInteger.valueOf(quarters))
                                .divide(BigInteger.valueOf(4))));
                    firstItem = false;
                } else adjusted.add(output);
            }
            return adjusted;
        };
    }

    @Inject(method = "applyRecipe", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$exactOutputs(GTRecipe recipe, ParallelHelper helper, OverclockCalculator calculator,
        CheckRecipeResult result, CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (!(helper instanceof BigRecipeParallelHelper)) return;
        CheckRecipeResult started = super.applyRecipe(recipe, helper, calculator, result);
        clearVars();
        cir.setReturnValue(started);
    }
}
