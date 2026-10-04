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

    private static final java.util.regex.Pattern FORMATTING = java.util.regex.Pattern
        .compile("\u00a7[0-9A-FK-OR]", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final String[] ACTIVE_RECIPE_KEYS = { "ApeironActualWirelessEUt", "ApeironActualWirelessEUtDisplay",
        "ApeironActualWirelessTotalEU", "ApeironActualWirelessTotalEUDisplay", "ApeironActualWirelessAmperageDisplay",
        "ApeironWirelessInputVoltage", "ApeironLosslessEnergy", "ApeironWirelessOutputRows",
        "ApeironWirelessOutputTypes" };

    public static String plainText(String text) {
        return FORMATTING.matcher(text)
            .replaceAll("");
    }

    private WirelessWailaDisplay() {}

    public static void write(NBTTagCompound tag, WirelessRecipeState state, int efficiency) {
        tag.setString(
            "ApeironParallelSetting",
            state.getParallelSettingBig()
                .signum() == 0 ? "∞" : BigNumberFormatter.formatCompact(state.getParallelSettingBig()));
        tag.setBoolean("ApeironWirelessRunning", state.isRunning());
        tag.setString(
            "ApeironRunningParallels",
            BigNumberFormatter.formatCompact(state.isRunning() ? state.getParallelsBig() : BigInteger.ZERO));
        tag.setString(
            "ApeironWirelessEUt",
            BigNumberFormatter.formatCompact(state.isRunning() ? state.displayEUt(efficiency) : BigInteger.ZERO));
        // A provider may reuse its compound after completion; never leave active-recipe display keys behind.
        for (String key : ACTIVE_RECIPE_KEYS) tag.removeTag(key);
        if (!state.isRunning()) return;
        final BigInteger actual = state.displayEUt(efficiency);
        tag.setString("ApeironActualWirelessEUt", actual.toString());
        tag.setString("ApeironActualWirelessEUtDisplay", BigNumberFormatter.formatCompact(actual));
        tag.setLong("ApeironWirelessInputVoltage", state.getVoltageSetting());
        tag.setString(
            "ApeironActualWirelessTotalEU",
            state.displayTotalEU(efficiency)
                .toString());
        tag.setString(
            "ApeironActualWirelessTotalEUDisplay",
            BigNumberFormatter.formatCompact(state.displayTotalEU(efficiency)));
        tag.setString(
            "ApeironActualWirelessAmperageDisplay",
            RecipeDisplayNumbers.rate(actual, state.getVoltageSetting(), 1));
        tag.setBoolean("ApeironLosslessEnergy", state.isLossless());
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
        if (tag == null) return;
        if (tag.hasKey("ApeironParallelSetting")) {
            final int nativeLimit = tag.getInteger("maxParallelRecipes");
            final String nativeLine = tag.hasKey("powerPanelMaxParallel")
                ? StatCollector.translateToLocalFormatted(
                    "GT5U.multiblock.parallelism_override",
                    tag.getInteger("powerPanelMaxParallel"),
                    nativeLimit)
                : StatCollector.translateToLocal("GT5U.multiblock.parallelism") + ": "
                    + EnumChatFormatting.WHITE
                    + nativeLimit;
            lines.remove(nativeLine);
            lines.add(
                StatCollector.translateToLocalFormatted(
                    "apeiron.machine.energy.parallel_status",
                    tag.getString("ApeironParallelSetting"),
                    tag.getString("ApeironRunningParallels")));
            lines.add("EU/t: " + tag.getString("ApeironWirelessEUt"));
        }
        if (!tag.hasKey("ApeironActualWirelessEUt")) return;
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
        if (tag == null || !tag.hasKey("ApeironParallelSetting") && !tag.hasKey("ApeironActualWirelessEUt"))
            return original;
        final List<String> lines = new ArrayList<>(original);
        for (int index = 0; index < lines.size(); index++) {
            final String plain = plainText(lines.get(index));
            if (plain.contains("并行限制") && tag.hasKey("ApeironParallelSetting")) {
                lines.set(
                    index,
                    EnumChatFormatting.AQUA + "并行限制： "
                        + EnumChatFormatting.YELLOW
                        + tag.getString("ApeironParallelSetting"));
                continue;
            }
            if (!tag.hasKey("ApeironActualWirelessEUt")) continue;
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
                    + GTUtility.getColoredTierNameFromVoltage(tag.getLong("ApeironWirelessInputVoltage")));
            else if (plain.contains("运行电压")) lines.set(
                index,
                EnumChatFormatting.LIGHT_PURPLE + "运行电压： "
                    + GTUtility.getColoredTierNameFromVoltage(tag.getLong("ApeironWirelessInputVoltage")));
        }
        return lines;
    }
}
