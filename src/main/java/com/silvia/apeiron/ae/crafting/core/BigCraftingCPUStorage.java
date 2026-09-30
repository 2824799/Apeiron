package com.silvia.apeiron.ae.crafting.core;

import java.math.BigInteger;

/** Exact byte counters exposed by a crafting CPU without changing AE's long API. */
public interface BigCraftingCPUStorage {

    BigInteger getAvailableStorageBig();

    BigInteger getUsedStorageBig();
}
