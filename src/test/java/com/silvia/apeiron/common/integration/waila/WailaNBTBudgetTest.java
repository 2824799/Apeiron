package com.silvia.apeiron.common.integration.waila;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.Test;

public class WailaNBTBudgetTest {

    @Test
    public void smallPacketsRemainIdentical() {
        assertNull(WailaNBTBudget.limit(null));
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("id", "test.machine");
        tag.setLong("energyUsage", 1024);
        tag.setByteArray("exact", new byte[] { 12, 34, 56 });
        assertSame(tag, WailaNBTBudget.limit(tag));
    }

    @Test
    public void hugeInventoryIsBoundedWithoutTouchingOriginal() throws Exception {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList inventory = new NBTTagList();
        byte[] payload = new byte[1024 * 1024];
        for (int i = 0; i < 360; i++) {
            NBTTagCompound item = new NBTTagCompound();
            item.setInteger("slot", i);
            item.setByteArray("payload", payload);
            inventory.appendTag(item);
        }
        tag.setTag("Inventory", inventory);
        NBTTagCompound limited = WailaNBTBudget.limit(tag);
        assertTrue(limited.getBoolean("ApeironWailaTruncated"));
        assertEquals(
            64,
            limited.getTagList("Inventory", 10)
                .tagCount());
        assertFalse(
            limited.getTagList("Inventory", 10)
                .getCompoundTagAt(0)
                .hasKey("payload"));
        assertEquals(360, inventory.tagCount());
        assertSame(
            payload,
            inventory.getCompoundTagAt(359)
                .getByteArray("payload"));
        assertFalse(tag.hasKey("ApeironWailaTruncated"));
        assertTrue(size(limited) <= WailaNBTBudget.MAX_BYTES);
    }

    @Test
    public void totalBudgetPreservesCoordinatesAndOperatingFields() throws Exception {
        NBTTagCompound tag = new NBTTagCompound();
        String text = String.join("", java.util.Collections.nCopies(4000, "矿"));
        for (int i = 0; i < 256; i++) tag.setString("large" + i, text);
        tag.setString("id", "test.machine");
        tag.setInteger("WailaX", 17);
        tag.setInteger("WailaY", 32);
        tag.setInteger("WailaZ", 65);
        tag.setInteger("mID", 31304);
        tag.setLong("energyUsage", 1024);
        NBTTagCompound limited = WailaNBTBudget.limit(tag);
        assertEquals("test.machine", limited.getString("id"));
        assertEquals(17, limited.getInteger("WailaX"));
        assertEquals(32, limited.getInteger("WailaY"));
        assertEquals(65, limited.getInteger("WailaZ"));
        assertEquals(31304, limited.getInteger("mID"));
        assertEquals(1024, limited.getLong("energyUsage"));
        assertTrue(size(limited) <= WailaNBTBudget.MAX_BYTES);
    }

    @Test
    public void deepTreesStopBeforeCompression() throws Exception {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound current = root;
        for (int i = 0; i < 1000; i++) {
            NBTTagCompound child = new NBTTagCompound();
            current.setTag("nested", child);
            current = child;
        }
        NBTTagCompound limited = WailaNBTBudget.limit(root);
        assertTrue(limited.getBoolean("ApeironWailaTruncated"));
        assertTrue(size(limited) <= WailaNBTBudget.MAX_BYTES);
    }

    @Test
    public void eyeRequirementsAndLegacyAliasesSurviveAnOversizedProvider() throws Exception {
        NBTTagCompound tag = new NBTTagCompound();
        for (int i = 0; i < 256; i++)
            tag.setString("large" + i, String.join("", java.util.Collections.nCopies(4000, "矿")));
        NBTTagCompound display = new NBTTagCompound();
        NBTTagList rows = new NBTTagList();
        NBTTagCompound hydrogen = new NBTTagCompound();
        hydrogen.setString("fluid", "hydrogen");
        hydrogen.setString("stored", "1234");
        hydrogen.setString("required", "10000000000");
        rows.appendTag(hydrogen);
        display.setTag("requirements", rows);
        tag.setTag("ApeironEyeOfHarmony", display);
        tag.setLong("stored.fluid.hydrogen", 1234);
        tag.setLong("eyeOfHarmonyOutputastralArrayAmount", 0);
        tag.setLong("eyeOfHarmonyOutputparallelAmount", 1);
        byte[] energy = java.math.BigInteger.TEN.pow(30)
            .negate()
            .toByteArray();
        tag.setByteArray("eyeOfHarmonyOutputusedEU", energy);
        NBTTagCompound result = WailaNBTBudget.limit(tag);
        assertEquals(display, result.getCompoundTag("ApeironEyeOfHarmony"));
        assertEquals(1234, result.getLong("stored.fluid.hydrogen"));
        assertTrue(result.hasKey("eyeOfHarmonyOutputastralArrayAmount"));
        assertEquals(1, result.getLong("eyeOfHarmonyOutputparallelAmount"));
        assertArrayEquals(energy, result.getByteArray("eyeOfHarmonyOutputusedEU"));
        assertTrue(size(result) <= WailaNBTBudget.MAX_BYTES);
    }

    @Test
    public void numericValuesAreNeverCutToAnIncorrectPrefix() {
        NBTTagCompound tag = new NBTTagCompound();
        String exact = java.math.BigInteger.TEN.pow(600)
            .add(java.math.BigInteger.ONE)
            .toString();
        tag.setString("amount", exact);
        assertSame(tag, WailaNBTBudget.limit(tag));
        tag.setString(
            "oversized",
            java.math.BigInteger.TEN.pow(5000)
                .toString());
        NBTTagCompound limited = WailaNBTBudget.limit(tag);
        assertEquals(exact, limited.getString("amount"));
        assertFalse(limited.hasKey("oversized"));
    }

    @Test
    public void arbitraryProviderScalarsSurviveNodeBudgetExhaustion() {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList inventory = new NBTTagList();
        for (int i = 0; i < 64; i++) {
            NBTTagCompound item = new NBTTagCompound();
            for (int j = 0; j < 64; j++) item.setLong("attribute" + j, j);
            inventory.appendTag(item);
        }
        tag.setTag("Inventory", inventory);
        tag.setLong("thirdPartyTemperature", 1234567L);
        tag.setString("thirdPartyPhase", "Cooling");
        NBTTagCompound result = WailaNBTBudget.limit(tag);
        assertTrue(result.getBoolean("ApeironWailaTruncated"));
        assertEquals(1234567L, result.getLong("thirdPartyTemperature"));
        assertEquals("Cooling", result.getString("thirdPartyPhase"));
    }

    private static int size(NBTTagCompound tag) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.write(tag, new DataOutputStream(bytes));
        return bytes.size();
    }
}
