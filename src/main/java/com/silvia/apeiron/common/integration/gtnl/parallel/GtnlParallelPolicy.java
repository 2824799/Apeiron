package com.silvia.apeiron.common.integration.gtnl.parallel;

import java.math.BigInteger;
import java.util.Objects;

import com.silvia.apeiron.api.machine.parallel.ParallelLimit;
import com.silvia.apeiron.common.machine.parallel.BigParallelPlan;
import com.silvia.apeiron.config.GtnlUnlimitedParallelConfig;

/** Pure planning for future complete GTNL adapters; no native machine logic or resources are changed here. */
public final class GtnlParallelPolicy {

    private GtnlParallelPolicy() {}

    /** Mirrors GT's alwaysMaxParallel/powerPanelMaxParallel choice without discarding a finite manual cap. */
    public static ParallelLimit getRequestedLimitBig(final GtnlParallelMachine machine, final BigInteger nativeMaximum,
        final boolean alwaysMaxParallel, final BigInteger powerPanelMaximum) {
        final boolean unlimited = GtnlUnlimitedParallelConfig.isRequested(machine, nativeMaximum);
        final ParallelLimit nativeLimit = unlimited ? ParallelLimit.unlimited() : ParallelLimit.bounded(nativeMaximum);
        if (alwaysMaxParallel) return nativeLimit;
        Objects.requireNonNull(powerPanelMaximum, "powerPanelMaximum");
        if (powerPanelMaximum.signum() < 0) throw new IllegalArgumentException("Negative power panel parallel maximum");
        final BigInteger panelBound = powerPanelMaximum.max(BigInteger.ONE);
        return ParallelLimit.bounded(nativeLimit.applyTo(panelBound));
    }

    /** Uses the same exact arithmetic and one-tick energy bound as the TST framework. */
    public static BigParallelPlan calculate(final GtnlParallelMachine machine, final BigInteger nativeMaximum,
        final boolean alwaysMaxParallel, final BigInteger powerPanelMaximum, final BigInteger inputParallelBound,
        final ParallelLimit outputLimit, final BigInteger availableEU, final BigInteger recipeEUt,
        final int durationTicks) {
        return BigParallelPlan.calculate(
            getRequestedLimitBig(machine, nativeMaximum, alwaysMaxParallel, powerPanelMaximum),
            inputParallelBound,
            outputLimit,
            availableEU,
            recipeEUt,
            durationTicks);
    }
}
