package com.silvia.apeiron.mixin.ae.terminal.pattern;

import java.math.BigInteger;
import java.util.HashSet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.crafting.core.BigOptimizerPattern;
import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.api.networking.crafting.ICraftingPatternDetails;

@Mixin(targets = "appeng.container.implementations.ContainerOptimizePatterns$Pattern", remap = false)
public abstract class OptimizerPatternBigMixin implements BigOptimizerPattern {

    @Shadow
    private HashSet<ICraftingPatternDetails> patternDetails;
    @Shadow
    private long requestedCrafts;
    @Unique
    private BigInteger apeiron$crafts = BigInteger.ZERO;

    @Override
    public void addCraftingTaskBig(ICraftingPatternDetails pattern, BigInteger crafts) {
        patternDetails.add(pattern);
        apeiron$crafts = apeiron$crafts.add(crafts);
        requestedCrafts = BigAEStackValues.saturatedLong(apeiron$crafts);
    }

    @Override
    public BigInteger getCraftsBig() {
        return apeiron$crafts;
    }

    @Inject(method = "addCraftingTask", at = @At("HEAD"), cancellable = true)
    private void apeiron$add(ICraftingPatternDetails pattern, long crafts, CallbackInfo ci) {
        addCraftingTaskBig(pattern, BigInteger.valueOf(crafts));
        ci.cancel();
    }
}
