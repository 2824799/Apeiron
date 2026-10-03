package com.silvia.apeiron.common.machine.energy;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.math.BigNumberFormatter;
import com.silvia.apeiron.math.RecipeDisplayNumbers;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.util.GTUtility;

public final class WirelessWailaDisplay {

    private WirelessWailaDisplay() {}

    public static void write(NBTTagCompound tag, WirelessRecipeState state, int efficiency) {
        if (!state.isRunning()) return;
        final BigInteger actual = RecipeDisplayNumbers.effectiveEUt(state.getEUtBig(), efficiency);
        tag.setString("ApeironActualWirelessEUt", actual.toString());
        tag.setString("ApeironActualWirelessEUtDisplay", BigNumberFormatter.formatCompact(actual));
        tag.setInteger("ApeironWirelessInputVoltage", Integer.MAX_VALUE);
        final int duration = state.getDuration();
        tag.setString(
            "ApeironActualWirelessTotalEU",
            actual.multiply(BigInteger.valueOf(duration))
                .toString());
        tag.setString(
            "ApeironActualWirelessTotalEUDisplay",
            BigNumberFormatter.formatCompact(actual.multiply(BigInteger.valueOf(duration))));
        tag.setString("ApeironActualWirelessAmperageDisplay", RecipeDisplayNumbers.rate(actual, Integer.MAX_VALUE, 1));
        NBTTagList rows = new NBTTagList();
        for (IAEStack<?> output : state.getHudOutputs()) {
            NBTTagCompound row = new NBTTagCompound();
            row.setString("name", output.getDisplayName());
            row.setString("amount", BigNumberFormatter.formatCompact(BigAEStackValues.get(output)));
            row.setBoolean("fluid", output instanceof IAEFluidStack);
            rows.appendTag(row);
        }
        tag.setTag("ApeironWirelessOutputRows", rows);
        tag.setInteger("ApeironWirelessOutputTypes", state.getOutputTypeCount());
    }

    public static void updateNative(List<String> lines, NBTTagCompound tag) {
        if (tag == null || !tag.hasKey("ApeironActualWirelessEUt")) return;
        final long legacy = tag.getLong("energyUsage");
        final int tier = (int) tag.getLong("energyTier");
        final String oldLine = tier > 0
            ? StatCollector.translateToLocalFormatted(
                "GT5U.waila.energy.use_with_amperage",
                com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber(legacy),
                GTUtility.getAmperageForTier(legacy, (byte) tier),
                GTUtility.getColoredTierNameFromTier((byte) tier))
            : StatCollector.translateToLocalFormatted(
                "GT5U.waila.energy.use",
                com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber(legacy),
                GTUtility.getColoredTierNameFromVoltage(legacy));
        final String newLine = StatCollector.translateToLocalFormatted(
            "GT5U.waila.energy.use",
            tag.getString("ApeironActualWirelessEUtDisplay"),
            StatCollector.translateToLocal("apeiron.machine.energy.wireless"));
        final int position = lines.indexOf(oldLine);
        if (position >= 0) lines.set(position, newLine);
        final NBTTagList outputs = tag.getTagList("ApeironWirelessOutputRows", 10);
        if (outputs.tagCount() > 0) {
            lines.add(StatCollector.translateToLocal("GT5U.waila.producing"));
            for (int i = 0; i < outputs.tagCount(); i++) {
                final NBTTagCompound output = outputs.getCompoundTagAt(i);
                lines.add(
                    "  " + EnumChatFormatting.AQUA
                        + output.getString("name")
                        + EnumChatFormatting.RESET
                        + " × "
                        + EnumChatFormatting.GOLD
                        + output.getString("amount")
                        + (output.getBoolean("fluid") ? " L" : ""));
            }
            int remaining = tag.getInteger("ApeironWirelessOutputTypes") - outputs.tagCount();
            if (remaining > 0)
                lines.add(StatCollector.translateToLocalFormatted("GT5U.waila.producing.andmore", remaining));
        }
    }

    /** OmniOcular's configurable upper rows come from full tile NBT and use JavaScript numbers. */
    public static List<String> updateOmni(List<String> original, NBTTagCompound tag) {
        if (tag == null || !tag.hasKey("ApeironActualWirelessEUt")) return original;
        final List<String> lines = new ArrayList<>(original);
        for (int index = 0; index < lines.size(); index++) {
            final String plain = EnumChatFormatting.getTextWithoutFormattingCodes(lines.get(index));
            if (plain.contains("功耗总额")) lines.set(
                index,
                EnumChatFormatting.LIGHT_PURPLE + "功耗总额： "
                    + EnumChatFormatting.YELLOW
                    + tag.getString("ApeironActualWirelessTotalEUDisplay")
                    + " EU");
            else if (plain.contains("机器功率")) lines.set(
                index,
                EnumChatFormatting.LIGHT_PURPLE + "机器功率： "
                    + EnumChatFormatting.YELLOW
                    + tag.getString("ApeironActualWirelessEUtDisplay")
                    + " EU/t");
            else if (plain.contains("MAX-Tier")) lines.set(
                index,
                EnumChatFormatting.LIGHT_PURPLE + "MAX-Tier： "
                    + EnumChatFormatting.YELLOW
                    + tag.getString("ApeironActualWirelessAmperageDisplay")
                    + " A "
                    + GTUtility.getColoredTierNameFromVoltage(tag.getInteger("ApeironWirelessInputVoltage")));
            else if (plain.contains("运行电压")) lines.set(
                index,
                EnumChatFormatting.LIGHT_PURPLE + "运行电压： "
                    + GTUtility.getColoredTierNameFromVoltage(tag.getInteger("ApeironWirelessInputVoltage")));
        }
        return lines;
    }
}
