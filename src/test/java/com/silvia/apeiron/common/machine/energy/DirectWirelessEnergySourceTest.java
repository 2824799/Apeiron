package com.silvia.apeiron.common.machine.energy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.UUID;

import org.junit.Test;

import com.silvia.apeiron.api.machine.energy.WirelessEnergyNetwork;

public class DirectWirelessEnergySourceTest {

    @Test
    public void debitsOneWholeBigAmountWithoutAPrereadOrLocalBuffer() {
        final FakeNetwork network = new FakeNetwork();
        final DirectWirelessEnergySource source = new DirectWirelessEnergySource(UUID.randomUUID(), network);
        final BigInteger amount = BigInteger.TEN.pow(80)
            .add(BigInteger.valueOf(3));
        network.balance = amount.add(BigInteger.ONE);
        assertTrue(source.consumeEUBig(amount));
        assertEquals(1, network.debits);
        assertEquals(0, network.reads);
        assertEquals(BigInteger.ONE, source.getAvailableEUBig());
        assertTrue(source.consumeEUBig(BigInteger.ZERO));
        assertEquals(1, network.debits);
        assertEquals(1, network.reads);
        assertThrows(IllegalArgumentException.class, () -> source.consumeEUBig(BigInteger.valueOf(-1)));
        assertEquals(1, network.debits);
    }

    @Test
    public void advisorySimulationDoesNotReserveAndAChangedBalanceIsRecheckedByDebit() {
        final FakeNetwork network = new FakeNetwork();
        network.balance = BigInteger.TEN;
        final DirectWirelessEnergySource source = new DirectWirelessEnergySource(UUID.randomUUID(), network);
        assertTrue(source.canConsumeEUBig(BigInteger.TEN));
        assertEquals(0, network.debits);
        network.balance = BigInteger.ONE;
        assertFalse(source.consumeEUBig(BigInteger.TEN));
        assertEquals(BigInteger.ONE, source.getAvailableEUBig());
    }

    private static final class FakeNetwork implements WirelessEnergyNetwork {

        private BigInteger balance = BigInteger.ZERO;
        private int reads;
        private int debits;

        @Override
        public BigInteger getAvailableEUBig(final UUID owner) {
            reads++;
            return balance;
        }

        @Override
        public boolean consumeEUBig(final UUID owner, final BigInteger amount) {
            debits++;
            if (balance.compareTo(amount) < 0) return false;
            balance = balance.subtract(amount);
            return true;
        }
    }
}
