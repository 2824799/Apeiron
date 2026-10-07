package com.silvia.apeiron.mixin.ae.crafting.core;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.crafting.core.BigTaskProgress;

import appeng.me.cluster.implementations.CraftingCPUCluster;

/** AE2 beta-1 uses Java 8 synthetic accessors for its private progress field. */
@Mixin(value = CraftingCPUCluster.TaskProgress.class, remap = false)
public abstract class LegacyTaskProgressAccessMixin {

    @Inject(method = "access$200", at = @At("HEAD"), cancellable = true)
    private static void apeiron$get(CraftingCPUCluster.TaskProgress progress, CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(((BigTaskProgress) progress).getValueLong());
    }

    @Inject(method = "access$210", at = @At("HEAD"), cancellable = true)
    private static void apeiron$decrement(CraftingCPUCluster.TaskProgress progress, CallbackInfoReturnable<Long> cir) {
        BigTaskProgress exact = (BigTaskProgress) progress;
        long previous = exact.getValueLong();
        exact.decrementValueBig();
        cir.setReturnValue(previous);
    }

    @Inject(method = "access$202", at = @At("HEAD"), cancellable = true)
    private static void apeiron$set(CraftingCPUCluster.TaskProgress progress, long value,
        CallbackInfoReturnable<Long> cir) {
        BigTaskProgress exact = (BigTaskProgress) progress;
        if (!exact.isValueBig() || value != Long.MAX_VALUE) exact.setValueBig(BigInteger.valueOf(value));
        cir.setReturnValue(value);
    }

    @Inject(method = "access$214", at = @At("HEAD"), cancellable = true)
    private static void apeiron$add(CraftingCPUCluster.TaskProgress progress, long value,
        CallbackInfoReturnable<Long> cir) {
        BigTaskProgress exact = (BigTaskProgress) progress;
        exact.setValueBig(
            exact.getValueBig()
                .add(BigInteger.valueOf(value)));
        cir.setReturnValue(exact.getValueLong());
    }
}
