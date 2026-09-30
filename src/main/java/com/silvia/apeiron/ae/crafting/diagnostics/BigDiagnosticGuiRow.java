package com.silvia.apeiron.ae.crafting.diagnostics;

import java.math.BigInteger;

/** Exact values and sort keys used by the client crafting-diagnostics table. */
public interface BigDiagnosticGuiRow {

    BigInteger getTotalProducedBig();

    BigInteger getElapsedTimeTicksBig();

    BigInteger getSampleCountBig();

    double getItemsPerSecondBig();

    String getDisplayNameForApeiron();

    void setExactGuiValues(BigInteger totalProduced, BigInteger elapsedTimeTicks, BigInteger sampleCount);
}
