package com.silvia.apeiron.api.machine.parallel;

import java.math.BigInteger;

/** The plant pays for its active units together, including units restored from a save. */
public interface BigPurificationPlant {

    BigInteger getPurificationPowerUsageBig();
}
