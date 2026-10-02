package com.silvia.apeiron.common.machine.energy;

import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;

import com.silvia.apeiron.api.machine.energy.BigWirelessEnergySource;
import com.silvia.apeiron.api.machine.energy.WirelessEnergyNetwork;
import com.silvia.apeiron.common.integration.gregtech.energy.GTWirelessEnergyNetwork;

/** Reusable account access for the planned Infinite Energy Hatch. It never stores or prefetches energy. */
public final class DirectWirelessEnergySource implements BigWirelessEnergySource {

    private final UUID owner;
    private final WirelessEnergyNetwork network;

    public DirectWirelessEnergySource(final UUID owner) {
        this(owner, GTWirelessEnergyNetwork.INSTANCE);
    }

    public DirectWirelessEnergySource(final UUID owner, final WirelessEnergyNetwork network) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.network = Objects.requireNonNull(network, "network");
    }

    @Override
    public UUID getOwnerUuid() {
        return owner;
    }

    @Override
    public BigInteger getAvailableEUBig() {
        return network.getAvailableEUBig(owner);
    }

    @Override
    public boolean canConsumeEUBig(final BigInteger amount) {
        validateAmount(amount);
        return amount.signum() == 0 || getAvailableEUBig().compareTo(amount) >= 0;
    }

    @Override
    public boolean consumeEUBig(final BigInteger amount) {
        validateAmount(amount);
        return amount.signum() == 0 || network.consumeEUBig(owner, amount);
    }

    private static void validateAmount(final BigInteger amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) throw new IllegalArgumentException("Wireless EU debit must be nonnegative");
    }
}
