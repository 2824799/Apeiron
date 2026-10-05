package com.silvia.apeiron.common.machine.tectech;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.common.machine.energy.WirelessWailaDisplay;
import com.silvia.apeiron.math.BigNumberFormatter;

/** Replaces the old planet-name estimate with the recipe requirements supplied by the server. */
public final class EyeOfHarmonyWailaDisplay {

    private EyeOfHarmonyWailaDisplay() {}

    public static List<String> updateOmni(List<String> original, NBTTagCompound tag) {
        return updateOmni(original, tag, EyeOfHarmonyWailaDisplay::fluidName);
    }

    static List<String> updateOmni(List<String> original, NBTTagCompound tag, Function<String, String> fluidName) {
        if (tag == null || !tag.hasKey("ApeironEyeOfHarmony", 10)) return original;
        List<String> lines = new ArrayList<>();
        boolean inLegacyRequirement = false;
        for (String line : original) {
            String plain = WirelessWailaDisplay.plainText(line)
                .trim();
            if (plain.startsWith("恒星等离子需求") || plain.startsWith("氢氦需求")) {
                inLegacyRequirement = true;
                continue;
            }
            if (inLegacyRequirement && (plain.isEmpty() || plain.startsWith("(") && plain.contains("L"))) continue;
            inLegacyRequirement = false;
            lines.add(line);
        }
        NBTTagList requirements = tag.getCompoundTag("ApeironEyeOfHarmony")
            .getTagList("requirements", 10);
        if (requirements.tagCount() > 0) {
            lines.add(
                EnumChatFormatting.GOLD + StatCollector.translateToLocal("apeiron.machine.eye.fluid_requirements"));
            for (int i = 0; i < requirements.tagCount(); i++) {
                NBTTagCompound row = requirements.getCompoundTagAt(i);
                String name = fluidName.apply(row.getString("fluid"));
                BigInteger stored = new BigInteger(row.getString("stored"));
                BigInteger required = new BigInteger(row.getString("required"));
                lines.add(
                    "  " + EnumChatFormatting.AQUA
                        + name
                        + EnumChatFormatting.RESET
                        + ": "
                        + EnumChatFormatting.YELLOW
                        + BigNumberFormatter.formatCompact(stored)
                        + " / "
                        + BigNumberFormatter.formatCompact(required)
                        + " L ("
                        + percent(stored, required)
                        + "%)");
            }
        }
        return lines;
    }

    private static String fluidName(String registryName) {
        Fluid fluid = FluidRegistry.getFluid(registryName);
        return fluid == null ? registryName : new FluidStack(fluid, 1).getLocalizedName();
    }

    private static String percent(BigInteger stored, BigInteger required) {
        if (required.signum() <= 0) return "100.00";
        return new BigDecimal(stored).multiply(BigDecimal.valueOf(100))
            .divide(new BigDecimal(required), 2, RoundingMode.HALF_UP)
            .toPlainString();
    }
}
