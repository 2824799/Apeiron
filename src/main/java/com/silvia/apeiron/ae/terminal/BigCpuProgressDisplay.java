package com.silvia.apeiron.ae.terminal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Locale;

import net.minecraft.util.EnumChatFormatting;

import com.silvia.apeiron.ae.crafting.core.BigCraftingCpuStatus;
import com.silvia.apeiron.math.BigNumberFormatter;

import appeng.container.implementations.CraftingCPUStatus;
import appeng.core.localization.GuiText;

public final class BigCpuProgressDisplay {

    private BigCpuProgressDisplay() {}

    public static BigInteger completed(CraftingCPUStatus cpu) {
        final BigInteger total = BigCraftingCpuStatus.totalItems(cpu)
            .max(BigInteger.ZERO);
        return total.subtract(BigCraftingCpuStatus.remainingItems(cpu))
            .max(BigInteger.ZERO)
            .min(total);
    }

    public static double fraction(CraftingCPUStatus cpu) {
        if (cpu == null) return 0;
        final BigInteger total = BigCraftingCpuStatus.totalItems(cpu);
        return total.signum() <= 0 ? 0
            : new BigDecimal(completed(cpu)).divide(new BigDecimal(total), MathContext.DECIMAL64)
                .doubleValue();
    }

    public static String tooltip(String original, CraftingCPUStatus cpu) {
        if (cpu == null || cpu.getCrafting() == null) return original;
        final String label = GuiText.Progress.getLocal() + ": ";
        final String replacement = EnumChatFormatting.GREEN + GuiText.Progress.getLocal()
            + EnumChatFormatting.RESET
            + ": "
            + BigNumberFormatter.formatExact(completed(cpu))
            + " / "
            + BigNumberFormatter.formatExact(BigCraftingCpuStatus.totalItems(cpu))
            + " ("
            + EnumChatFormatting.GOLD
            + String.format(Locale.ROOT, "%.2f%%", fraction(cpu) * 100)
            + EnumChatFormatting.RESET
            + ") ";
        final String[] lines = original.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            if (EnumChatFormatting.getTextWithoutFormattingCodes(lines[index])
                .startsWith(label)) lines[index] = replacement;
        }
        return String.join("\n", lines);
    }
}
