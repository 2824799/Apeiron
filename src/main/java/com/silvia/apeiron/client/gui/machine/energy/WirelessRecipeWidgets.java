package com.silvia.apeiron.client.gui.machine.energy;

import java.math.BigInteger;
import java.util.List;
import java.util.function.IntSupplier;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.FluidDisplayWidget;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.modularui.api.drawable.FluidDrawable;
import com.gtnewhorizons.modularui.api.drawable.ItemDrawable;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.MultiChildWidget;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.math.BigNumberFormatter;
import com.silvia.apeiron.math.RecipeDisplayNumbers;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.modularui2.GTWidgetThemes;
import gregtech.api.util.GTUtility;

/** Display-only output rows; their identities and quantities never enter native output inventories. */
public final class WirelessRecipeWidgets {

    private WirelessRecipeWidgets() {}

    public static String amountLine(IAEStack<?> stack, int duration) {
        final BigInteger amount = BigAEStackValues.get(stack);
        final String count = BigAEStackValues.fitsLong(amount) ? GTUtility.formatShortenedLong(amount.longValue())
            : BigNumberFormatter.formatCompact(amount);
        return "× " + EnumChatFormatting.GOLD
            + count
            + EnumChatFormatting.RESET
            + (stack instanceof IAEFluidStack ? " L" : "")
            + " ("
            + RecipeDisplayNumbers.rate(amount, duration, 20)
            + "/s)";
    }

    public static String tooltip(IAEStack<?> stack, int duration) {
        final BigInteger amount = BigAEStackValues.get(stack);
        final String unit = stack instanceof IAEFluidStack ? " L" : "";
        final StringBuilder text = new StringBuilder(EnumChatFormatting.AQUA + stack.getDisplayName() + "\n");
        text.append(StatCollector.translateToLocal("GT5U.gui.text.amount"))
            .append(" ")
            .append(EnumChatFormatting.GOLD)
            .append(BigNumberFormatter.formatExact(amount))
            .append(unit)
            .append(EnumChatFormatting.RESET);
        final String[] keys = { "apeiron.machine.energy.per_tick", "GT5U.gui.text.per_second",
            "GT5U.gui.text.per_minute", "GT5U.gui.text.per_hour", "GT5U.gui.text.per_day" };
        final long[] ticks = { 1, 20, 1200, 72000, 1728000 };
        for (int index = 0; index < keys.length; index++) text.append("\n")
            .append(StatCollector.translateToLocal(keys[index]))
            .append(" ")
            .append(EnumChatFormatting.GOLD)
            .append(RecipeDisplayNumbers.rate(amount, duration, ticks[index]))
            .append(unit)
            .append(EnumChatFormatting.RESET);
        return text.toString();
    }

    public static IWidget modern(List<IAEStack<?>> outputs, IntSupplier duration) {
        final Flow column = Flow.column()
            .crossAxisAlignment(Alignment.CrossAxis.START)
            .coverChildren(0);
        for (IAEStack<?> stack : outputs) {
            final IAEStack<?> identity = BigAEStackValues.copyWithSize(stack, BigInteger.ONE);
            final IWidget icon = stack instanceof IAEItemStack ? new ItemDisplayWidget().disableThemeBackground(true)
                .displayAmount(false)
                .item(((IAEItemStack) identity).getItemStack())
                .size(14)
                .marginRight(2)
                : new FluidDisplayWidget().widgetTheme(GTWidgetThemes.BACKGROUND_TERMINAL)
                    .displayAmount(false)
                    .value(((IAEFluidStack) identity).getFluidStack())
                    .size(14)
                    .marginRight(2);
            column.child(
                Flow.row()
                    .fullWidth()
                    .height(15)
                    .child(icon)
                    .child(
                        Flow.column()
                            .coverChildren(0)
                            .crossAxisAlignment(Alignment.CrossAxis.START)
                            .child(
                                new com.cleanroommc.modularui.widgets.TextWidget<>(
                                    IKey.str(EnumChatFormatting.AQUA + stack.getDisplayName())).height(8)
                                        .scale(0.75f))
                            .child(
                                new com.cleanroommc.modularui.widgets.TextWidget<>(
                                    IKey.dynamic(() -> amountLine(stack, duration.getAsInt()))).height(6)
                                        .scale(0.6f))
                            .tooltip(t -> t.addLine(tooltip(stack, duration.getAsInt())))));
        }
        return column;
    }

    public static DynamicPositionedColumn legacy(List<IAEStack<?>> outputs, IntSupplier duration) {
        final DynamicPositionedColumn column = new DynamicPositionedColumn();
        for (IAEStack<?> stack : outputs) {
            final IAEStack<?> identity = BigAEStackValues.copyWithSize(stack, BigInteger.ONE);
            com.gtnewhorizons.modularui.api.widget.Widget icon = stack instanceof IAEItemStack
                ? new ItemDrawable(((IAEItemStack) identity).getItemStack()).asWidget()
                : new FluidDrawable().setFluid(
                    ((IAEFluidStack) stack).getFluidStack()
                        .getFluid())
                    .asWidget();
            column.widget(
                new MultiChildWidget().addChild(
                    icon.setSize(8, 8)
                        .setPos(0, 0))
                    .addChild(
                        com.gtnewhorizons.modularui.common.widget.TextWidget
                            .dynamicString(() -> stack.getDisplayName() + " " + amountLine(stack, duration.getAsInt()))
                            .addTooltip(tooltip(stack, duration.getAsInt()))
                            .setPos(10, 1)));
        }
        return column;
    }
}
