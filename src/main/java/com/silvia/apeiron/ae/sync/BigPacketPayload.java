package com.silvia.apeiron.ae.sync;

import java.math.BigInteger;
import java.util.Map;

import appeng.api.storage.data.IAEStack;
import appeng.me.diagnostics.DiagnosticRowView;

/** Allows a compatible AE packet to append an optional Apeiron payload. */
public interface BigPacketPayload {

    void appendApeironBigInteger(BigInteger value);

    void appendApeironFlowRates(Map<IAEStack<?>, ?> rates);

    void appendApeironDiagnosticRows(java.util.List<DiagnosticRowView> rows);
}
