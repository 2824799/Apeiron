package com.silvia.apeiron.ae.crafting.diagnostics;

import java.math.BigInteger;

import appeng.me.cluster.implementations.CraftingCpuDiagnostics;

/** Exact production counters used by AE crafting diagnostics. */
public interface BigCraftingTimingRecord {

    BigInteger getRemainingToProduceBig();

    BigInteger getOriginalToProduceBig();

    void addRemainingToProduceBig(BigInteger delta);

    void addProducedBig(BigInteger delta);

    /** Bridges AE's protected completion marker to the diagnostics owner. */
    void setEndTickBig(long endTick);

    static BigInteger remaining(final CraftingCpuDiagnostics.CraftingTimingRecord record) {
        return record instanceof BigCraftingTimingRecord
                ? ((BigCraftingTimingRecord) record).getRemainingToProduceBig()
                : BigInteger.valueOf(record.getRemainingToProduce());
    }

    static BigInteger original(final CraftingCpuDiagnostics.CraftingTimingRecord record) {
        return record instanceof BigCraftingTimingRecord
                ? ((BigCraftingTimingRecord) record).getOriginalToProduceBig()
                : BigInteger.valueOf(record.getOriginalToProduce());
    }
}
