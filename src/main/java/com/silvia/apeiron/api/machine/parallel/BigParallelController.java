package com.silvia.apeiron.api.machine.parallel;

import java.math.BigInteger;

/** Shared contract for complete controller adapters. Legacy projections must never drive exact execution. */
public interface BigParallelController {

    ParallelLimit getParallelLimitBig();

    BigInteger getCurrentParallelsBig();

    BigInteger getRecipeEUtBig();

    BigInteger getRecipeTotalEUBig();
}
