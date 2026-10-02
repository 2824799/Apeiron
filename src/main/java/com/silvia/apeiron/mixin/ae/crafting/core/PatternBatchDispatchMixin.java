package com.silvia.apeiron.mixin.ae.crafting.core;

import java.math.BigInteger;
import java.util.Map;

import net.minecraft.inventory.InventoryCrafting;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.llamalad7.mixinextras.sugar.Local;
import com.silvia.apeiron.ae.crafting.core.BigTaskProgress;
import com.silvia.apeiron.ae.crafting.core.PatternBatchDispatch;
import com.silvia.apeiron.ae.crafting.core.UnlimitedCraftingCPU;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;

@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class PatternBatchDispatchMixin {

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

    @Redirect(
        method = "executeCrafting",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingMedium;pushPattern(Lappeng/api/networking/crafting/ICraftingPatternDetails;Lnet/minecraft/inventory/InventoryCrafting;)Z"))
    private boolean apeiron$batch(ICraftingMedium medium, ICraftingPatternDetails details, InventoryCrafting table,
        @Local(argsOnly = true) IEnergyGrid energy) {
        if (!(medium instanceof MTEInfinitePatternInputAssembly) || details.isCraftable() || details.isInputOnly())
            return medium.pushPattern(details, table);
        CraftingCPUCluster.TaskProgress progress = tasks.get(details);
        if (progress == null) return false;
        boolean unlimited = ((UnlimitedCraftingCPU) this).isCraftingParallelUnlimited();
        BigInteger limit = ((BigTaskProgress) progress).getValueBig();
        if (!unlimited) limit = limit.min(BigInteger.valueOf(Math.max(1, remainingOperations)));
        BigInteger pushed = PatternBatchDispatch
            .dispatch((MTEInfinitePatternInputAssembly) medium, details, table, inventory, limit, energy);
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
        if (((UnlimitedCraftingCPU) cpu).isCraftingParallelUnlimited()) return 16383;
        return Math.min(cpu.getCoProcessors(), Integer.MAX_VALUE - 1);
    }
}
