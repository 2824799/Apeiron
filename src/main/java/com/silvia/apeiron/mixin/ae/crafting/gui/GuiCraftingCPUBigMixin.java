package com.silvia.apeiron.mixin.ae.crafting.gui;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.silvia.apeiron.ae.crafting.core.BigCraftingCpuEntry;
import com.silvia.apeiron.ae.terminal.BigGuiNumberCapture;

import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.container.implementations.CraftingCpuEntry;
import appeng.util.ReadableNumberConverter;

/** Renders exact quantities in the crafting CPU terminal and its tooltips. */
@Mixin(value = GuiCraftingCPU.class, remap = false)
public abstract class GuiCraftingCPUBigMixin {

    @Redirect(
        method = "drawEntry",
        at = @At(value = "INVOKE", target = "Lappeng/container/implementations/CraftingCpuEntry;getStoredAmount()J"))
    private long apeiron$stored(final CraftingCpuEntry entry) {
        return BigGuiNumberCapture.captureAmount(BigCraftingCpuEntry.stored(entry));
    }

    @Redirect(
        method = "drawEntry",
        at = @At(value = "INVOKE", target = "Lappeng/container/implementations/CraftingCpuEntry;getActiveAmount()J"))
    private long apeiron$active(final CraftingCpuEntry entry) {
        return BigGuiNumberCapture.captureAmount(BigCraftingCpuEntry.active(entry));
    }

    @Redirect(
        method = "drawEntry",
        at = @At(value = "INVOKE", target = "Lappeng/container/implementations/CraftingCpuEntry;getPendingAmount()J"))
    private long apeiron$pending(final CraftingCpuEntry entry) {
        return BigGuiNumberCapture.captureAmount(BigCraftingCpuEntry.pending(entry));
    }

    @Redirect(
        method = "drawAmountLine",
        at = @At(value = "INVOKE", target = "Lappeng/util/ReadableNumberConverter;toWideReadableForm(J)Ljava/lang/String;"))
    private String apeiron$formatWide(final ReadableNumberConverter converter, final long value) {
        return BigGuiNumberCapture.formatWideAmount(value);
    }

    @Redirect(
        method = "drawAmountLine",
        at = @At(value = "INVOKE", target = "Ljava/text/NumberFormat;format(J)Ljava/lang/String;"))
    private String apeiron$formatExact(final java.text.NumberFormat formatter, final long value) {
        return BigGuiNumberCapture.formatExactAmount(value);
    }
}
