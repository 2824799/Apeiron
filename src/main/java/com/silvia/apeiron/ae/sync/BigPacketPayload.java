package com.silvia.apeiron.ae.sync;

import java.math.BigInteger;
import java.util.Map;

import appeng.api.storage.data.IAEStack;
import appeng.me.cache.ItemFlowGridCache.FlowRate;
import appeng.me.diagnostics.DiagnosticRowView;

/** Allows a compatible AE packet to append an optional Apeiron payload. */
public interface BigPacketPayload {

    void appendApeironBigInteger(BigInteger value);

    void appendApeironFlowRates(Map<IAEStack<?>, FlowRate> rates);

    void appendApeironDiagnosticRows(java.util.List<DiagnosticRowView> rows);
}
