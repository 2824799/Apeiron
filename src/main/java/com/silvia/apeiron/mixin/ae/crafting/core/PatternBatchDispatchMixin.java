package com.silvia.apeiron.mixin.ae.crafting.core;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.inventory.InventoryCrafting;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;
import com.silvia.apeiron.ae.crafting.core.BigTaskProgress;
import com.silvia.apeiron.ae.crafting.core.PatternBatchDispatch;
import com.silvia.apeiron.ae.crafting.core.PatternBatchMedium;
import com.silvia.apeiron.ae.crafting.core.UnlimitedCraftingCPU;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.crafting.TunnelPatternInputs;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;

@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class PatternBatchDispatchMixin {

    @Unique
    private static final List<IAEStack<?>> apeiron$invalidTunnelInputs = Collections
        .unmodifiableList(Collections.emptyList());

    @Shadow
    @Final
    protected Map<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> tasks;
    @Shadow
    protected MECraftingInventory inventory;
    @Shadow
    protected IItemList<IAEStack<?>> waitingFor;
    @Shadow
    protected MachineSource machineSrc;
    @Shadow
    protected int remainingOperations;

    @Shadow
    protected abstract void postChange(IAEStack<?> stack, BaseActionSource source);

    @Shadow
    protected abstract void postCraftingStatusChange(IAEStack<?> stack);

    @Inject(method = "getExpandedCondensedInputs", at = @At("HEAD"), cancellable = true, require = 0)
    private void apeiron$expandCondensedTunnels(ICraftingPatternDetails pattern, CraftingGridCache cache,
        CallbackInfoReturnable<List<IAEStack<?>>> cir) {
        IAEStack<?>[] declared = pattern.getCondensedAEInputs();
        if (cache == null || !TunnelPatternInputs.hasReferences(pattern, declared)) return;
        List<IAEStack<?>> expanded = TunnelPatternInputs.expand(declared, TunnelPatternInputs.definitions(cache));
        cir.setReturnValue(
            expanded == null ? apeiron$invalidTunnelInputs : Arrays.asList(TunnelPatternInputs.condense(expanded)));
    }

    @Inject(method = "getExpandedInputs", at = @At("HEAD"), cancellable = true, require = 0)
    private void apeiron$expandTunnels(ICraftingPatternDetails pattern, CraftingGridCache cache,
        CallbackInfoReturnable<List<IAEStack<?>>> cir) {
        IAEStack<?>[] declared = pattern.getAEInputs();
        if (cache == null || !TunnelPatternInputs.hasReferences(pattern, declared)) return;
        cir.setReturnValue(TunnelPatternInputs.expand(declared, TunnelPatternInputs.definitions(cache)));
    }

    @Inject(
        method = "canCraft(Lappeng/api/networking/crafting/ICraftingPatternDetails;Ljava/util/List;)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 0)
    private void apeiron$skipInvalidTunnels(ICraftingPatternDetails pattern, List<IAEStack<?>> inputs,
        CallbackInfoReturnable<Boolean> cir) {
        if (inputs == apeiron$invalidTunnelInputs) cir.setReturnValue(false);
    }

    @Redirect(
        method = "executeCrafting",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingMedium;pushPattern(Lappeng/api/networking/crafting/ICraftingPatternDetails;Lnet/minecraft/inventory/InventoryCrafting;)Z"))
    private boolean apeiron$batch(ICraftingMedium medium, ICraftingPatternDetails details, InventoryCrafting table,
        @Local(argsOnly = true) IEnergyGrid energy, @Local(argsOnly = true) CraftingGridCache cache) {
        if (!(medium instanceof PatternBatchMedium) || details.isCraftable() || details.isInputOnly())
            return medium.pushPattern(details, table);
        CraftingCPUCluster.TaskProgress progress = tasks.get(details);
        if (progress == null) return false;
        boolean unlimited = ((UnlimitedCraftingCPU) this).isCraftingParallelUnlimited();
        BigInteger limit = ((BigTaskProgress) progress).getValueBig();
        if (!unlimited) limit = limit.min(BigInteger.valueOf(Math.max(1, remainingOperations)));
        BigInteger pushed = PatternBatchDispatch.dispatch(
            (PatternBatchMedium) medium,
            details,
            table,
            inventory,
            limit,
            energy,
            TunnelPatternInputs.definitions(cache));
        if (pushed.signum() == 0) return false;
        BigInteger extra = pushed.subtract(BigInteger.ONE);
        if (extra.signum() > 0) {
            ((BigTaskProgress) progress).setValueBig(
                ((BigTaskProgress) progress).getValueBig()
                    .subtract(extra));
            if (!unlimited) remainingOperations -= extra.intValueExact();
            for (IAEStack<?> output : details.getCondensedAEOutputs()) {
                IAEStack<?> pending = BigAEStackValues.copyWithSize(
                    output,
                    BigAEStackValues.get(output)
                        .multiply(extra));
                waitingFor.add(pending.copy());
                postChange(pending, machineSrc);
                postCraftingStatusChange(pending);
            }
        }
        return true;
    }

    /** A per-tick work budget keeps ordinary physical interfaces responsive; exact batches are unbounded. */
    @Redirect(
        method = "updateCraftingLogic",
        at = @At(
            value = "FIELD",
            target = "Lappeng/me/cluster/implementations/CraftingCPUCluster;accelerator:I",
            opcode = Opcodes.GETFIELD))
    private int apeiron$workBudget(CraftingCPUCluster cpu) {
        if (((UnlimitedCraftingCPU) cpu).isCraftingParallelUnlimited()) return 1_048_575;
        return Math.min(cpu.getCoProcessors(), Integer.MAX_VALUE - 1);
    }
}
