package com.silvia.apeiron.mixin.ae.crafting.core;

import java.math.BigInteger;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.silvia.apeiron.ae.crafting.core.BigTaskProgress;

import appeng.me.cluster.implementations.CraftingCPUCluster;

/** AE2 beta-2 and later access progress fields directly. */
@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class ModernTaskProgressAccessMixin {

    @Redirect(
        method = { "executeCrafting", "mergeJob", "writeToNBT", "readFromNBT" },
        at = @At(
            value = "FIELD",
            target = "Lappeng/me/cluster/implementations/CraftingCPUCluster$TaskProgress;value:J",
            opcode = Opcodes.GETFIELD))
    private long apeiron$getTaskProgress(final CraftingCPUCluster.TaskProgress progress) {
        return ((BigTaskProgress) progress).getValueLong();
    }

    @Redirect(
        method = { "executeCrafting", "mergeJob", "writeToNBT", "readFromNBT" },
        at = @At(
            value = "FIELD",
            target = "Lappeng/me/cluster/implementations/CraftingCPUCluster$TaskProgress;value:J",
            opcode = Opcodes.PUTFIELD))
    private void apeiron$setTaskProgress(final CraftingCPUCluster.TaskProgress progress, final long value) {
        final BigTaskProgress exact = (BigTaskProgress) progress;
        if (exact.isValueBig() && value == Long.MAX_VALUE - 1L) {
            exact.decrementValueBig();
        } else if (exact.isValueBig() && value == Long.MAX_VALUE) {
            // A legacy assignment copied the saturated view. Keep the exact sidecar.
        } else {
            exact.setValueBig(BigInteger.valueOf(value));
        }
    }

}
