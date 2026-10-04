package com.silvia.apeiron.mixin.ae.crafting.core;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.silvia.apeiron.ae.crafting.diagnostics.BigCraftingGridDiagnostics;
import com.silvia.apeiron.ae.crafting.diagnostics.BigCraftingNetworkDiagnostics;

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
