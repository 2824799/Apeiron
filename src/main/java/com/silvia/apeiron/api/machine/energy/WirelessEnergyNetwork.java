package com.silvia.apeiron.api.machine.energy;

import java.math.BigInteger;
import java.util.UUID;

/** Injectable backend for direct energy sources. All operations are performed on the server thread. */
public interface WirelessEnergyNetwork {

    BigInteger getAvailableEUBig(UUID owner);

    boolean consumeEUBig(UUID owner, BigInteger amount);
}
