package com.silvia.apeiron.ae.crafting.diagnostics;

import java.math.BigInteger;

import appeng.me.cluster.implementations.CraftingCpuDiagnostics;

/** Exact produced amount in one completed crafting diagnostic record. */
public interface BigCompletedDiagnosticRecord {

    BigInteger getProducedAmountBig();

    static BigInteger produced(final CraftingCpuDiagnostics.CompletedDiagnosticRecord record) {
        return record instanceof BigCompletedDiagnosticRecord
            ? ((BigCompletedDiagnosticRecord) record).getProducedAmountBig()
            : BigInteger.valueOf(record.getProducedAmount());
    }
}
