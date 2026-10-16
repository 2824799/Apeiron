package com.silvia.apeiron.mixin.ae.crafting.core;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.silvia.apeiron.ae.crafting.core.BigTaskProgress;
import com.silvia.apeiron.ae.crafting.core.CraftingPatternComparator;

import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.me.cluster.implementations.CraftingCPUCluster;

/** Install one collision-safe order for the task, execution and merge-backup maps. */
@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class CraftingCPUTaskOrderingMixin {

    @Shadow
    @Final
    @Mutable
    protected Map<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> tasks;

    @Shadow
    @Final
    @Mutable
    protected Map<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> workableTasks;

    @Shadow
    @Final
    @Mutable
    protected Comparator<ICraftingPatternDetails> priorityComparator;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void apeiron$installTaskOrdering(final CallbackInfo ci) {
        priorityComparator = new CraftingPatternComparator();
        tasks = new TreeMap<>(priorityComparator);
        workableTasks = new TreeMap<>(priorityComparator);
    }

    @Inject(
        method = "submitJob",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingJob;startCrafting(Lappeng/crafting/MECraftingInventory;Lappeng/api/networking/crafting/ICraftingCPU;Lappeng/api/networking/security/BaseActionSource;)V"))
    private void apeiron$beginTaskOrdering(final CallbackInfoReturnable<ICraftingLink> cir) {
        // Native submitJob has already rejected occupied CPUs or delegated to mergeJob here.
        // The previous tick's work list is no longer used; release collision keys between jobs.
        workableTasks.clear();
        ((CraftingPatternComparator) priorityComparator).clear();
    }

    @WrapOperation(
        method = "mergeJob",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/me/cluster/implementations/CraftingCPUCluster$TaskProgress;copyDiagnosticSessionsFrom(Lappeng/me/cluster/implementations/CraftingCPUCluster$TaskProgress;)V"))
    private void apeiron$copyExactBackupProgress(CraftingCPUCluster.TaskProgress backup,
        CraftingCPUCluster.TaskProgress original, Operation<Void> operation) {
        operation.call(backup, original);
        // The native backup copies only the saturated long value before copying diagnostics.
        ((BigTaskProgress) backup).setValueBig(((BigTaskProgress) original).getValueBig());
    }
}
