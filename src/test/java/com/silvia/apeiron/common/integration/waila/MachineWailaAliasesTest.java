package com.silvia.apeiron.common.integration.waila;

import static org.junit.Assert.*;

import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

public class MachineWailaAliasesTest {

    private enum Phase {
        Heating,
        Cooling
    }

    public static class Parent {

        private long temperature = 12345;
        private final BigInteger charge = BigInteger.TEN.pow(30)
            .add(BigInteger.ONE);
        private final Phase phase = Phase.Cooling;

        public void saveNBTData(NBTTagCompound tag) {
            tag.setLong("mTemperature", temperature);
            tag.setByteArray("signedEU", charge.toByteArray());
            tag.setString("decimalEU", charge.toString(10));
            tag.setString("phase", phase.toString());
            throw new IllegalStateException("HUD must never invoke the serializer");
        }
    }

    public static class Child extends Parent {

        private int cycles = 3;
        private int adjusted = 10;

        @Override
        public void saveNBTData(NBTTagCompound tag) {
            tag.setInteger("mCycles", cycles);
            tag.setInteger("adjusted", adjusted + 7);
            super.saveNBTData(tag);
        }
    }

    @Test
    public void saveAliasesAreInheritedAndExactWithoutExecutingSave() {
        NBTTagCompound tag = new NBTTagCompound();
        MachineWailaAliases.write(new Child(), tag);
        assertEquals(12345L, tag.getLong("mTemperature"));
        assertEquals(3, tag.getInteger("mCycles"));
        assertEquals(
            BigInteger.TEN.pow(30)
                .add(BigInteger.ONE),
            new BigInteger(tag.getByteArray("signedEU")));
        assertEquals(
            BigInteger.TEN.pow(30)
                .add(BigInteger.ONE)
                .toString(),
            tag.getString("decimalEU"));
        assertEquals("Cooling", tag.getString("phase"));
        assertFalse(tag.hasKey("adjusted"));
    }

    @Test
    public void nativeProviderValueHasPrecedence() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("mTemperature", 67890L);
        MachineWailaAliases.write(new Child(), tag);
        assertEquals(67890L, tag.getLong("mTemperature"));
    }
}
