package com.silvia.apeiron.mixin.ae.terminal.pattern;

import java.text.NumberFormat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.silvia.apeiron.ae.terminal.BigGuiNumberCapture;

import appeng.api.storage.data.IAEStack;
import appeng.client.gui.implementations.GuiOptimizePatterns;
import appeng.util.ReadableNumberConverter;

/** Keeps pattern optimizer request counts readable when requestable values exceed long. */
@Mixin(value = GuiOptimizePatterns.class, remap = false)
public abstract class GuiOptimizePatternsBigMixin {

    @Redirect(
        method = "drawFG",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IAEStack;getCountRequestableCrafts()J"))
    private long apeiron$captureCrafts(final IAEStack<?> stack) {
        return BigGuiNumberCapture.captureCrafts(stack);
    }

    @Redirect(
        method = "drawFG",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IAEStack;getCountRequestable()J"))
    private long apeiron$captureRequestable(final IAEStack<?> stack) {
        return BigGuiNumberCapture.captureRequestable(stack);
    }

    @Redirect(
        method = "drawFG",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/util/ReadableNumberConverter;toWideReadableForm(J)Ljava/lang/String;"))
    private String apeiron$formatWide(final ReadableNumberConverter converter, final long value) {
        return BigGuiNumberCapture.formatOptimizerWide(value);
    }

    @Redirect(
        method = "drawFG",
        at = @At(value = "INVOKE", target = "Ljava/text/NumberFormat;format(J)Ljava/lang/String;"))
    private String apeiron$formatExact(final NumberFormat formatter, final long value) {
        if (BigGuiNumberCapture.hasPendingCrafts()) return BigGuiNumberCapture.formatExactCrafts(value);
        return BigGuiNumberCapture.formatOptimizerRequestable(value);
    }

    @Redirect(
        method = "updateMultipliers",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IAEStack;getCountRequestableCrafts()J"))
    private long apeiron$captureUpdateCrafts(final IAEStack<?> stack) {
        return BigGuiNumberCapture.captureCrafts(stack);
    }

    @Redirect(
        method = "updateMultipliers",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IAEStack;getCountRequestable()J"))
    private long apeiron$captureUpdateRequestable(final IAEStack<?> stack) {
        return BigGuiNumberCapture.captureRequestable(stack);
    }

    @Redirect(
        method = "updateMultipliers",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/container/implementations/ContainerOptimizePatterns;getBitMultiplier(JJJ)I"))
    private int apeiron$exactMultiplier(final long currentCrafts, final long perCraft, final long maximumCrafts) {
        return BigGuiNumberCapture.getBitMultiplier(currentCrafts, perCraft, maximumCrafts);
    }
}
