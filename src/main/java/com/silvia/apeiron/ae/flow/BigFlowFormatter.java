package com.silvia.apeiron.ae.flow;

import java.math.BigInteger;

import net.minecraft.util.EnumChatFormatting;

import com.silvia.apeiron.math.BigNumberFormatter;

import appeng.core.AEConfig;
import appeng.core.localization.ButtonToolTips;
import appeng.me.cache.ItemFlowGridCache.FlowRate;

/** Exact tooltip formatter for AE item-flow rates. */
public final class BigFlowFormatter {

    private BigFlowFormatter() {}

    public static String format(final FlowRate rate) {
        final BigFlowRate exact = (BigFlowRate) rate;
        final BigInteger in = exact.inBig();
        final BigInteger out = exact.outBig();
        final String window = Math.max(1, AEConfig.instance.itemFlowTrackingWindowMinutes) + "m";
        final String inText = EnumChatFormatting.GREEN + "+"
            + BigNumberFormatter.formatExact(in)
            + EnumChatFormatting.RESET;
        final String outText = EnumChatFormatting.RED + "-"
            + BigNumberFormatter.formatExact(out)
            + EnumChatFormatting.RESET;
        if (in.signum() == 0) return ButtonToolTips.FlowRateTooltipShort.getLocal(window, outText);
        if (out.signum() == 0) return ButtonToolTips.FlowRateTooltipShort.getLocal(window, inText);

        final BigInteger net = in.subtract(out);
        final String netText = (net.signum() >= 0 ? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
            + (net.signum() >= 0 ? "+" : "-")
            + BigNumberFormatter.formatExact(net.abs())
            + EnumChatFormatting.RESET;
        return ButtonToolTips.FlowRateTooltipFull.getLocal(window, inText, outText, netText);
    }
}
