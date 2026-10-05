package com.silvia.apeiron.common.machine.tectech;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.Test;

public class EyeOfHarmonyWailaDisplayTest {

    @Test
    public void unrelatedMachinesKeepTheirTooltip() {
        List<String> original = Arrays.asList("machine status", "other mod information");
        assertSame(original, EyeOfHarmonyWailaDisplay.updateOmni(original, new NBTTagCompound()));
        assertSame(original, EyeOfHarmonyWailaDisplay.updateOmni(original, null));
    }

    @Test
    public void actualHydrogenAndHeliumReplaceTheBrokenStellarEstimate() {
        List<String> original = Arrays.asList("running recipe", "§6恒星等离子需求   NaN %", "§b(0L/0L)", "other status");
        NBTTagCompound tag = tag(row("test.hydrogen", "250", "1000"), row("test.helium", "0", "2000"));
        List<String> result = EyeOfHarmonyWailaDisplay.updateOmni(original, tag, name -> name);
        assertTrue(result.contains("running recipe"));
        assertTrue(result.contains("other status"));
        assertTrue(
            result.stream()
                .anyMatch(line -> line.contains("250 / 1000 L (25.00%)")));
        assertTrue(
            result.stream()
                .anyMatch(line -> line.contains("0 / 2000 L (0.00%)")));
        assertFalse(
            result.stream()
                .anyMatch(line -> line.contains("NaN") || line.contains("0L/0L")));
        assertEquals(4, original.size());
    }

    @Test
    public void splitHydrogenHeliumRowsAreReplacedAndBigNumbersStayExact() {
        List<String> original = Arrays.asList("§6氢氦需求", "", "(0L/10GL氢)", "(0L/10GL氦)", "other status");
        List<String> result = EyeOfHarmonyWailaDisplay
            .updateOmni(original, tag(row("test.plasma", "9007199254740993", "18014398509481986")), name -> name);
        assertTrue(result.contains("other status"));
        assertTrue(
            result.stream()
                .anyMatch(line -> line.contains("9007199254740993 / 18014398509481986 L (50.00%)")));
        assertFalse(
            result.stream()
                .anyMatch(line -> line.contains("10GL")));
    }

    @Test
    public void noRecipeProducesNoFabricatedRequirementOrZeroDivision() {
        List<String> result = EyeOfHarmonyWailaDisplay
            .updateOmni(Arrays.asList("恒星等离子需求 NaN %", "(0L/0L)", "idle"), tag());
        assertEquals(Arrays.asList("idle"), result);
        result = EyeOfHarmonyWailaDisplay.updateOmni(new ArrayList<>(), tag(row("test.fluid", "0", "0")), name -> name);
        assertTrue(
            result.stream()
                .anyMatch(line -> line.contains("100.00%")));
    }

    private static NBTTagCompound tag(NBTTagCompound... rows) {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound display = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound row : rows) list.appendTag(row);
        display.setTag("requirements", list);
        tag.setTag("ApeironEyeOfHarmony", display);
        return tag;
    }

    private static NBTTagCompound row(String fluid, String stored, String required) {
        NBTTagCompound row = new NBTTagCompound();
        row.setString("fluid", fluid);
        row.setString("stored", stored);
        row.setString("required", required);
        return row;
    }
}
