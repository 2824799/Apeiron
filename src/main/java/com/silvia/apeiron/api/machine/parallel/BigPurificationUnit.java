package com.silvia.apeiron.api.machine.parallel;

import java.math.BigInteger;

import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;

/** Exact water output remains available to native stage-specific output bonuses. */
public interface BigPurificationUnit {

    BigInteger getPurificationParallelSettingBig();

    void setPurificationParallelSettingBig(BigInteger value);

    BigInteger getPurificationParallelsBig();

    BigInteger getPurificationPowerUsageBig();

    BigInteger getPurificationWaterOutputBig();

    boolean isPurificationOutputPendingBig();

    boolean copyPurificationRecipeFluidsBig(BigMachineOutputQueue target);
}
