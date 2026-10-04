package com.silvia.apeiron.mixin.ae.terminal.pattern;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.crafting.core.BigOptimizerPattern;
import com.silvia.apeiron.crafting.BigCraftingTree;

import appeng.crafting.v2.resolvers.CraftableItemResolver.CraftFromPatternTask;

@Mixin(targets = "appeng.container.implementations.ContainerOptimizePatterns$Pattern", remap = false)
public abstract class LegacyOptimizerTaskMixin {

    @Inject(
        method = "addCraftingTask(Lappeng/crafting/v2/resolvers/CraftableItemResolver$CraftFromPatternTask;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$add(CraftFromPatternTask task, CallbackInfo ci) {
        ((BigOptimizerPattern) this).addCraftingTaskBig(
            task.pattern,
            task instanceof BigCraftingTree.PatternTask ? ((BigCraftingTree.PatternTask) task).getExactCrafts()
                : BigInteger.valueOf(task.getTotalCraftsDone()));
        ci.cancel();
    }
}
