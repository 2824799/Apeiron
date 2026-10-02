package com.silvia.apeiron.bench;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.UUID;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import com.silvia.apeiron.common.machine.energy.DirectWirelessEnergySource;

import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;

/** Account state exists only inside the benchmark fork; no actual world is loaded or saved. */
@State(Scope.Thread)
public class WirelessEnergyBenchmark {

    @Param({ "63", "256", "4096" })
    public int balanceBits;

    @Param({ "1", "1024" })
    public int accounts;

    @Param({ "one", "halfBalance" })
    public String debitMagnitude;

    private UUID[] owners;
    private DirectWirelessEnergySource[] sources;
    private int cursor;
    private BigInteger debit;
    private BigInteger negativeDebit;

    @Setup(Level.Iteration)
    public void resetAccounts() {
        GlobalVariableStorage.GlobalEnergy = new HashMap<>();
        SpaceProjectManager.spaceTeams = new HashMap<>();
        GlobalEnergyWorldSavedData.INSTANCE = new GlobalEnergyWorldSavedData();
        owners = new UUID[accounts];
        sources = new DirectWirelessEnergySource[accounts];
        final BigInteger balance = BigInteger.ONE.shiftLeft(balanceBits)
            .subtract(BigInteger.ONE);
        for (int i = 0; i < accounts; i++) {
            owners[i] = new UUID(0L, i + 1L);
            sources[i] = new DirectWirelessEnergySource(owners[i]);
            WirelessNetworkManager.setUserEU(owners[i], balance);
        }
        debit = "one".equals(debitMagnitude) ? BigInteger.ONE : BigInteger.ONE.shiftLeft(balanceBits / 2);
        negativeDebit = debit.negate();
        cursor = 0;
    }

    @Benchmark
    public boolean gregTechDebit() {
        cursor = (cursor + 1) & (accounts - 1);
        return WirelessNetworkManager.addEUToGlobalEnergyMap(owners[cursor], negativeDebit);
    }

    @Benchmark
    public boolean apeironDebit() {
        cursor = (cursor + 1) & (accounts - 1);
        return sources[cursor].consumeEUBig(debit);
    }
}
