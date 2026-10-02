package com.silvia.apeiron.mixin.ae.crafting.core;

import java.math.BigInteger;
import java.util.concurrent.Future;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.crafting.diagnostics.BigCraftingGridDiagnostics;
import com.silvia.apeiron.ae.crafting.diagnostics.BigCraftingNetworkDiagnostics;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.crafting.BigCraftingJobFast;

import appeng.api.config.CraftingMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.me.cache.CraftingGridCache;
import appeng.me.diagnostics.CraftingDiagnosticSessionId;
import appeng.me.diagnostics.CraftingNetworkDiagnostics;

/** Routes large requests through Apeiron's exact fast planner. */
@Mixin(value = CraftingGridCache.class, remap = false)
public abstract class CraftingGridCacheMixin
    implements com.silvia.apeiron.ae.storage.BigMEInventory, BigCraftingGridDiagnostics {

    @Shadow
    @Final
    protected CraftingNetworkDiagnostics diagnostics;

    @Shadow
    protected long diagnosticsRevision;

    @Inject(
        method = "beginCraftingJob(Lnet/minecraft/world/World;Lappeng/api/networking/IGrid;Lappeng/api/networking/security/BaseActionSource;Lappeng/api/storage/data/IAEStack;Lappeng/api/config/CraftingMode;ZLappeng/api/networking/crafting/ICraftingCallback;)Ljava/util/concurrent/Future;",
        at = @At("HEAD"),
        cancellable = true)
    private void apeiron$beginExactJob(final World world, final IGrid grid, final BaseActionSource source,
        final IAEStack<?> stack, final CraftingMode mode, final boolean lite, final ICraftingCallback callback,
        final CallbackInfoReturnable<Future<ICraftingJob>> cir) {
        if (stack == null || !BigAEStackValues.isBig(stack)) return;
        final BigCraftingJobFast<?> job = new BigCraftingJobFast<>(
            world,
            grid,
            source,
            (IAEStack) stack,
            mode,
            callback);
        cir.setReturnValue((Future) job.schedule());
    }

    @Override
    public IAEStack<?> injectItemsBig(final IAEStack<?> input, final appeng.api.config.Actionable mode,
        final BaseActionSource source) {
        return ((CraftingGridCache) (Object) this).injectItems((IAEStack) input, mode, source);
    }

    @Override
    public IAEStack<?> extractItemsBig(final IAEStack<?> request, final appeng.api.config.Actionable mode,
        final BaseActionSource source) {
        return null;
    }

    @Override
    public void recordDiagnosticSampleBig(final IAEStack<?> output, final CraftingDiagnosticSessionId sessionId,
        final BigInteger producedAmount, final long observedStartTick, final long observedEndTick) {
        ((BigCraftingNetworkDiagnostics) (Object) this.diagnostics)
            .recordSampleBig(output, sessionId, producedAmount, observedStartTick, observedEndTick);
        this.diagnosticsRevision = this.diagnostics.getRevision();
    }
}
