package com.silvia.apeiron.ae.crafting.diagnostics;

import java.math.BigInteger;

/** Exact values carried by one crafting-diagnostics table row. */
public interface BigDiagnosticRow {

    BigInteger getTotalProducedBig();

    BigInteger getElapsedTimeTicksBig();

    BigInteger getSampleCountBig();

    void setExactValues(BigInteger totalProduced, BigInteger elapsedTimeTicks, BigInteger sampleCount);
}
