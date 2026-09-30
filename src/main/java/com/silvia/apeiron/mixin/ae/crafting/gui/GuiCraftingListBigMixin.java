package com.silvia.apeiron.mixin.ae.crafting.gui;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.silvia.apeiron.ae.terminal.BigGuiNumberCapture;

import appeng.api.storage.data.IAEStack;
import appeng.client.gui.widgets.GuiCraftingList;
import appeng.util.ReadableNumberConverter;

/** Uses exact crafting-list values in both the live view and screenshots. */
@Mixin(value = GuiCraftingList.class, remap = false)
public abstract class GuiCraftingListBigMixin {

    @Redirect(
        method = "drawStringAndItem",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IAEStack;getStackSize()J"))
    private static long apeiron$captureStack(final IAEStack<?> stack) {
        return BigGuiNumberCapture.captureStack(stack);
    }

    @Redirect(
        method = "drawStringAndItem",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IAEStack;getCountRequestableCrafts()J"))
    private static long apeiron$captureCrafts(final IAEStack<?> stack) {
        return BigGuiNumberCapture.captureCrafts(stack);
    }

    @Redirect(
        method = "drawStringAndItem",
        at = @At(value = "INVOKE", target = "Lappeng/util/ReadableNumberConverter;toWideReadableForm(J)Ljava/lang/String;"))
    private static String apeiron$formatWide(final ReadableNumberConverter converter, final long value) {
        return BigGuiNumberCapture.formatWideAny(value);
    }
}
