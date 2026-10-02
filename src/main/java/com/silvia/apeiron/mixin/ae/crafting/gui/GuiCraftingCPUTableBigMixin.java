package com.silvia.apeiron.mixin.ae.crafting.gui;

import java.text.NumberFormat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.terminal.BigCpuProgressDisplay;
import com.silvia.apeiron.ae.terminal.BigGuiNumberCapture;

import appeng.api.storage.data.IAEStack;
import appeng.client.gui.widgets.GuiCraftingCPUTable;
import appeng.container.implementations.CraftingCPUStatus;
import appeng.util.ReadableNumberConverter;

/** Keeps crafting-CPU table item quantities exact. */
@Mixin(value = GuiCraftingCPUTable.class, remap = false)
public abstract class GuiCraftingCPUTableBigMixin {

    @Unique
    private CraftingCPUStatus apeiron$progressCpu;

    @Unique
    private CraftingCPUStatus apeiron$hoveredCpu;

    @Shadow
    public abstract CraftingCPUStatus hitCpu(int x, int y);

    @Inject(method = "drawFG", at = @At("HEAD"), require = 1)
    private void apeiron$begin(int offsetX, int offsetY, int mouseX, int mouseY, int guiLeft, int guiTop,
        CallbackInfo ci) {
        BigGuiNumberCapture.clear();
        apeiron$hoveredCpu = hitCpu(mouseX - guiLeft, mouseY - guiTop);
        apeiron$progressCpu = null;
    }

    @Redirect(
        method = "drawFG",
        at = @At(value = "INVOKE", target = "Lappeng/container/implementations/CraftingCPUStatus;getTotalItems()J"),
        require = 1)
    private long apeiron$captureProgress(CraftingCPUStatus cpu) {
        apeiron$progressCpu = cpu;
        return cpu.getTotalItems();
    }

    @ModifyVariable(method = "drawFG", at = @At("STORE"), ordinal = 0, require = 1)
    private double apeiron$exactProgressBar(double legacy) {
        return BigCpuProgressDisplay.fraction(apeiron$progressCpu);
    }

    @ModifyArg(
        method = "drawFG",
        at = @At(value = "INVOKE", target = "Lappeng/client/gui/AEBaseGui;drawTooltip(IILjava/lang/String;)V"),
        index = 2,
        require = 1)
    private String apeiron$exactProgressTooltip(String original) {
        return BigCpuProgressDisplay.tooltip(original, apeiron$hoveredCpu);
    }

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
        at = @At(
            value = "INVOKE",
            target = "Lappeng/util/ReadableNumberConverter;toWideReadableForm(J)Ljava/lang/String;"))
    private String apeiron$formatWide(final ReadableNumberConverter converter, final long value) {
        return BigGuiNumberCapture.formatWideAny(value);
    }
}
