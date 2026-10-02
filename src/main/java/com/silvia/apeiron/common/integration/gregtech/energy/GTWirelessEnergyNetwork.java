package com.silvia.apeiron.common.integration.gregtech.energy;

import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;

import com.silvia.apeiron.api.machine.energy.WirelessEnergyNetwork;

import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.WirelessNetworkManager;

/** Uses GregTech's team-aware BigInteger API and existing world save; never changes its map directly. */
public enum GTWirelessEnergyNetwork implements WirelessEnergyNetwork {

    INSTANCE;

    @Override
    public BigInteger getAvailableEUBig(final UUID owner) {
        Objects.requireNonNull(owner, "owner");
        return GlobalEnergyWorldSavedData.INSTANCE == null ? BigInteger.ZERO : WirelessNetworkManager.getUserEU(owner);
    }

    @Override
    public boolean consumeEUBig(final UUID owner, final BigInteger amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) throw new IllegalArgumentException("Wireless EU debit must be nonnegative");
        if (amount.signum() == 0) return true;
        return GlobalEnergyWorldSavedData.INSTANCE != null
            && WirelessNetworkManager.addEUToGlobalEnergyMap(owner, amount.negate());
    }
}
