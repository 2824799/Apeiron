package com.silvia.apeiron.common.integration.gregtech.energy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.silvia.apeiron.common.machine.energy.DirectWirelessEnergySource;

import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;

public class GTWirelessEnergyNetworkTest {

    private HashMap<UUID, BigInteger> previousEnergy;
    private Map<UUID, UUID> previousTeams;
    private GlobalEnergyWorldSavedData previousSavedData;

    @Before
    public void isolateGregTechAccountState() {
        previousEnergy = GlobalVariableStorage.GlobalEnergy;
        previousTeams = SpaceProjectManager.spaceTeams;
        previousSavedData = GlobalEnergyWorldSavedData.INSTANCE;
        GlobalVariableStorage.GlobalEnergy = new HashMap<>();
        SpaceProjectManager.spaceTeams = new HashMap<>();
        GlobalEnergyWorldSavedData.INSTANCE = new GlobalEnergyWorldSavedData();
    }

    @After
    public void restoreGregTechAccountState() {
        GlobalVariableStorage.GlobalEnergy = previousEnergy;
        SpaceProjectManager.spaceTeams = previousTeams;
        GlobalEnergyWorldSavedData.INSTANCE = previousSavedData;
    }

    @Test
    public void realNetworkDebitAndWorldSavePreserveExactAmounts() {
        final UUID owner = UUID.randomUUID();
        final BigInteger amount = BigInteger.TEN.pow(100)
            .add(BigInteger.valueOf(17));
        final DirectWirelessEnergySource source = new DirectWirelessEnergySource(owner);
        final BigInteger remaining = amount.add(BigInteger.valueOf(23));
        WirelessNetworkManager.setUserEU(owner, amount.add(remaining));
        assertTrue(source.canConsumeEUBig(amount));
        assertEquals(amount.add(remaining), source.getAvailableEUBig());
        assertTrue(source.consumeEUBig(amount));
        assertEquals(remaining, source.getAvailableEUBig());
        assertFalse(source.consumeEUBig(remaining.add(BigInteger.ONE)));
        assertEquals(remaining, source.getAvailableEUBig());
        final NBTTagCompound saved = new NBTTagCompound();
        GlobalEnergyWorldSavedData.INSTANCE.writeToNBT(saved);
        GlobalVariableStorage.GlobalEnergy.clear();
        GlobalEnergyWorldSavedData.INSTANCE.readFromNBT(saved);
        assertEquals(remaining, source.getAvailableEUBig());
    }

    @Test
    public void ownershipTracksTeamChangesAndUnavailableWorldsCannotBeDebited() {
        final UUID owner = UUID.randomUUID();
        final UUID leader = UUID.randomUUID();
        WirelessNetworkManager.setUserEU(owner, BigInteger.valueOf(100));
        WirelessNetworkManager.setUserEU(leader, BigInteger.valueOf(200));
        final DirectWirelessEnergySource source = new DirectWirelessEnergySource(owner);
        SpaceProjectManager.putInTeam(owner, leader);
        assertTrue(source.consumeEUBig(BigInteger.valueOf(30)));
        assertEquals(BigInteger.valueOf(170), source.getAvailableEUBig());
        SpaceProjectManager.putInTeam(owner, owner);
        assertEquals(BigInteger.valueOf(100), source.getAvailableEUBig());
        GlobalEnergyWorldSavedData.INSTANCE = null;
        assertFalse(source.consumeEUBig(BigInteger.ONE));
        assertEquals(BigInteger.ZERO, source.getAvailableEUBig());
    }
}
