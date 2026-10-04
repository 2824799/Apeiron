package com.silvia.apeiron.mixin.ae.terminal.pattern;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.crafting.core.BigOptimizerPattern;

import appeng.api.networking.crafting.ICraftingPatternDetails;

@Mixin(targets = "appeng.container.implementations.ContainerOptimizePatterns$Pattern", remap = false)
public abstract class ModernOptimizerTaskMixin {

    @Inject(
        method = "addCraftingTask(Lappeng/api/networking/crafting/ICraftingPatternDetails;J)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$add(ICraftingPatternDetails pattern, long crafts, CallbackInfo ci) {
        ((BigOptimizerPattern) this).addCraftingTaskBig(pattern, BigInteger.valueOf(crafts));
        ci.cancel();
    }
}
