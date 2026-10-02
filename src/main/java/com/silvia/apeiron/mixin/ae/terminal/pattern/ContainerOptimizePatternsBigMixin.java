package com.silvia.apeiron.mixin.ae.terminal.pattern;

import java.util.HashMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.crafting.core.BigOptimizerPattern;
import com.silvia.apeiron.ae.stack.BigAERequestableStack;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.crafting.BigCraftingJobFast;

import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.storage.data.IAEStack;
import appeng.container.implementations.ContainerOptimizePatterns;
import appeng.crafting.v2.CraftingContext;

@Mixin(value = ContainerOptimizePatterns.class, remap = false)
public abstract class ContainerOptimizePatternsBigMixin {

    @Shadow
    private HashMap<IAEStack<?>, Object> patterns;
    @Shadow
    private ICraftingJob result;

    @ModifyVariable(method = "setResult", at = @At("STORE"), ordinal = 0)
    private CraftingContext apeiron$context(CraftingContext original) {
        return result instanceof BigCraftingJobFast ? ((BigCraftingJobFast<?>) result).getContext() : original;
    }

    @Inject(
        method = "setResult",
        at = @At(value = "INVOKE", target = "Ljava/util/HashMap;entrySet()Ljava/util/Set;", ordinal = 0))
    private void apeiron$populate(ICraftingJob job, CallbackInfo ci,
        @com.llamalad7.mixinextras.sugar.Local codechicken.nei.ItemStackSet blacklistedPatterns) {
        if (!(job instanceof BigCraftingJobFast)) return;
        try {
            java.lang.reflect.Constructor<?> constructor = Class
                .forName("appeng.container.implementations.ContainerOptimizePatterns$Pattern")
                .getDeclaredConstructor();
            constructor.setAccessible(true);
            ((BigCraftingJobFast<?>) job).forEachPatternBig((pattern, crafts) -> {
                if (pattern.isCraftable() || blacklistedPatterns.contains(pattern.getPattern())) return;
                for (IAEStack<?> output : pattern.getCondensedAEOutputs()) {
                    Object entry = patterns.get(output);
                    if (entry == null) {
                        try {
                            entry = constructor.newInstance();
                        } catch (ReflectiveOperationException error) {
                            throw new IllegalStateException(error);
                        }
                        patterns.put(output, entry);
                    }
                    ((BigOptimizerPattern) entry).addCraftingTaskBig(pattern, crafts);
                }
            });
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("AE optimizer entry construction failed", error);
        }
    }

    @Redirect(
        method = "setResult",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IAEStack;setCountRequestableCrafts(J)Lappeng/api/storage/data/IAEStack;"))
    private IAEStack<?> apeiron$crafts(IAEStack<?> stack, long crafts) {
        Object entry = patterns.get(stack);
        return entry instanceof BigOptimizerPattern
            ? ((BigAERequestableStack) stack).setCountRequestableCraftsBig(((BigOptimizerPattern) entry).getCraftsBig())
            : stack.setCountRequestableCrafts(crafts);
    }

    @Redirect(
        method = "setResult",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IAEStack;setCountRequestable(J)Lappeng/api/storage/data/IAEStack;"))
    private IAEStack<?> apeiron$perCraft(IAEStack<?> stack, long amount) {
        Object entry = patterns.get(stack);
        if (entry != null) {
            try {
                java.lang.reflect.Method getPattern = entry.getClass()
                    .getDeclaredMethod("getPattern");
                getPattern.setAccessible(true);
                appeng.api.networking.crafting.ICraftingPatternDetails pattern = (appeng.api.networking.crafting.ICraftingPatternDetails) getPattern
                    .invoke(entry);
                for (IAEStack<?> output : pattern.getCondensedAEOutputs()) if (output.equals(stack))
                    return ((BigAERequestableStack) stack).setCountRequestableBig(BigAEStackValues.get(output));
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
        }
        return stack.setCountRequestable(amount);
    }
}
