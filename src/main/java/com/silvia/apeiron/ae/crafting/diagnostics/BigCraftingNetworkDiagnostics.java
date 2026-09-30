package com.silvia.apeiron.ae.crafting.diagnostics;

import java.math.BigInteger;

import appeng.api.storage.data.IAEStack;
import appeng.me.diagnostics.CraftingDiagnosticSessionId;

/** Exact production-count entry point for AE's crafting diagnostics cache. */
public interface BigCraftingNetworkDiagnostics {

    void recordSampleBig(IAEStack<?> output, CraftingDiagnosticSessionId sessionId, BigInteger producedAmount,
        long observedStartTick, long observedEndTick);
}
