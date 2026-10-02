package com.silvia.apeiron.api.machine.energy;

import java.math.BigInteger;
import java.util.UUID;

/** Server-thread access to a wireless account. No local EU buffer and no cached account balance. */
public interface BigWirelessEnergySource {

    UUID getOwnerUuid();

    BigInteger getAvailableEUBig();

    /** Advisory check only; another machine may consume the same account before the debit. */
    boolean canConsumeEUBig(BigInteger amount);

    /** Debits the whole nonnegative amount once, or leaves the account unchanged and returns false. */
    boolean consumeEUBig(BigInteger amount);
}
