package com.silvia.apeiron.mixin.ae.crafting.gui;

import java.text.NumberFormat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.silvia.apeiron.ae.terminal.BigGuiNumberCapture;

import appeng.api.storage.data.IAEStack;
import appeng.client.gui.widgets.GuiCraftingCPUTable;
import appeng.util.ReadableNumberConverter;

/** Keeps crafting-CPU table item quantities exact. */
@Mixin(value = GuiCraftingCPUTable.class, remap = false)
public abstract class GuiCraftingCPUTableBigMixin {

    @Redirect(
        method = "drawFG",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IAEStack;getStackSize()J"))
    private long apeiron$captureStack(final IAEStack<?> stack) {
        return BigGuiNumberCapture.captureStack(stack);
    }

    @Redirect(
        method = "drawFG",
        at = @At(value = "INVOKE", target = "Ljava/text/NumberFormat;format(J)Ljava/lang/String;"))
    private String apeiron$formatExact(final NumberFormat formatter, final long value) {
        return BigGuiNumberCapture.formatExactAny(value);
    }

    @Redirect(
        method = "drawFG",
        at = @At(value = "INVOKE", target = "Lappeng/util/ReadableNumberConverter;toWideReadableForm(J)Ljava/lang/String;"))
    private String apeiron$formatWide(final ReadableNumberConverter converter, final long value) {
        return BigGuiNumberCapture.formatWideAny(value);
    }
}
