package com.silvia.apeiron.ae.crafting.diagnostics;

import java.math.BigInteger;

import appeng.api.storage.data.IAEStack;
import appeng.me.diagnostics.CraftingDiagnosticSessionId;

/** Exact diagnostic bridge exposed by the live crafting-grid cache. */
public interface BigCraftingGridDiagnostics {

    void recordDiagnosticSampleBig(IAEStack<?> output, CraftingDiagnosticSessionId sessionId, BigInteger producedAmount,
        long observedStartTick, long observedEndTick);
}
