package com.silvia.apeiron.mixin.ae.terminal.core;

import java.text.NumberFormat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.terminal.BigNetworkStatus;
import com.silvia.apeiron.ae.terminal.BigGuiNumberCapture;

import appeng.api.storage.data.IAEStack;
import appeng.client.gui.implementations.GuiNetworkStatus;
import appeng.container.implementations.ContainerNetworkStatus;
import appeng.util.Platform;

/** Keeps the network-status inventory and energy tooltip quantities exact. */
@Mixin(value = GuiNetworkStatus.class, remap = false)
public abstract class GuiNetworkStatusBigMixin {

    @Inject(method = "drawConsume", at = @At("HEAD"))
    private void apeiron$beginConsume(final CallbackInfo ci) {
        BigGuiNumberCapture.beginNetworkDisplay(0);
    }

    @Inject(method = "drawItemInfo", at = @At("HEAD"))
    private void apeiron$beginItem(final CallbackInfo ci) {
        BigGuiNumberCapture.beginNetworkDisplay(1);
    }

    @Inject(method = "drawFluidInfo", at = @At("HEAD"))
    private void apeiron$beginFluid(final CallbackInfo ci) {
        BigGuiNumberCapture.beginNetworkDisplay(2);
    }

    @Inject(method = "drawEssentiaInfo", at = @At("HEAD"))
    private void apeiron$beginEssentia(final CallbackInfo ci) {
        BigGuiNumberCapture.beginNetworkDisplay(3);
    }

    private static BigNetworkStatus apeiron$exact(final ContainerNetworkStatus container) {
        return (BigNetworkStatus) (Object) container;
    }

    @Redirect(method = { "drawConsume", "drawItemInfo", "drawFluidInfo", "drawEssentiaInfo" },
        at = @At(value = "INVOKE", target = "Lappeng/container/implementations/ContainerNetworkStatus;getItemBytesTotal()J"))
    private long apeiron$itemBytesTotal(final ContainerNetworkStatus container) {
        return BigGuiNumberCapture.captureNetworkBytes(1, apeiron$exact(container).getItemBytesTotalBig(),
            container.getItemBytesTotal());
    }

    @Redirect(method = { "drawConsume", "drawItemInfo", "drawFluidInfo", "drawEssentiaInfo" },
        at = @At(value = "INVOKE", target = "Lappeng/container/implementations/ContainerNetworkStatus;getItemBytesUsed()J"))
    private long apeiron$itemBytesUsed(final ContainerNetworkStatus container) {
        return BigGuiNumberCapture.captureNetworkBytes(0, apeiron$exact(container).getItemBytesUsedBig(),
            container.getItemBytesUsed());
    }

    @Redirect(method = { "drawConsume", "drawItemInfo", "drawFluidInfo", "drawEssentiaInfo" },
        at = @At(value = "INVOKE", target = "Lappeng/container/implementations/ContainerNetworkStatus;getFluidBytesTotal()J"))
    private long apeiron$fluidBytesTotal(final ContainerNetworkStatus container) {
        return BigGuiNumberCapture.captureNetworkBytes(3, apeiron$exact(container).getFluidBytesTotalBig(),
            container.getFluidBytesTotal());
    }

    @Redirect(method = { "drawConsume", "drawItemInfo", "drawFluidInfo", "drawEssentiaInfo" },
        at = @At(value = "INVOKE", target = "Lappeng/container/implementations/ContainerNetworkStatus;getFluidBytesUsed()J"))
    private long apeiron$fluidBytesUsed(final ContainerNetworkStatus container) {
        return BigGuiNumberCapture.captureNetworkBytes(2, apeiron$exact(container).getFluidBytesUsedBig(),
            container.getFluidBytesUsed());
    }

    @Redirect(method = { "drawConsume", "drawItemInfo", "drawFluidInfo", "drawEssentiaInfo" },
        at = @At(value = "INVOKE", target = "Lappeng/container/implementations/ContainerNetworkStatus;getEssentiaBytesTotal()J"))
    private long apeiron$essentiaBytesTotal(final ContainerNetworkStatus container) {
        return BigGuiNumberCapture.captureNetworkBytes(5, apeiron$exact(container).getEssentiaBytesTotalBig(),
            container.getEssentiaBytesTotal());
    }

    @Redirect(method = { "drawConsume", "drawItemInfo", "drawFluidInfo", "drawEssentiaInfo" },
        at = @At(value = "INVOKE", target = "Lappeng/container/implementations/ContainerNetworkStatus;getEssentiaBytesUsed()J"))
    private long apeiron$essentiaBytesUsed(final ContainerNetworkStatus container) {
        return BigGuiNumberCapture.captureNetworkBytes(4, apeiron$exact(container).getEssentiaBytesUsedBig(),
            container.getEssentiaBytesUsed());
    }

    @Redirect(method = { "drawConsume", "drawItemInfo", "drawFluidInfo", "drawEssentiaInfo" },
        at = @At(value = "INVOKE", target = "Lappeng/util/Platform;formatByteDouble(D)Ljava/lang/String;"))
    private static String apeiron$formatBytes(final double value) {
        return BigGuiNumberCapture.formatNetworkBytes(value);
    }

    @Redirect(method = { "drawItemInfo", "drawFluidInfo", "drawEssentiaInfo" },
        at = @At(value = "INVOKE", target = "Ljava/lang/StringBuilder;append(J)Ljava/lang/StringBuilder;"))
    private static StringBuilder apeiron$formatNetworkInteger(final StringBuilder builder, final long value) {
        return builder.append(BigGuiNumberCapture.formatNetworkInteger(value));
    }

    @Redirect(method = "drawItemInfo", at = @At(value = "INVOKE", target = "Lappeng/container/implementations/ContainerNetworkStatus;getItemCellCount()J"))
    private long apeiron$itemCellCount(final ContainerNetworkStatus container) {
        return BigGuiNumberCapture.captureNetworkInteger(6, apeiron$exact(container).getItemCellCountBig());
    }

    @Redirect(method = "drawItemInfo", at = @At(value = "INVOKE", target = "Lappeng/container/implementations/ContainerNetworkStatus;getItemTypesUsed()J"))
    private long apeiron$itemTypesUsed(final ContainerNetworkStatus container) {
        return BigGuiNumberCapture.captureNetworkInteger(7, apeiron$exact(container).getItemTypesUsedBig());
    }

    @Redirect(method = "drawItemInfo", at = @At(value = "INVOKE", target = "Lappeng/container/implementations/ContainerNetworkStatus;getItemTypesTotal()J"))
    private long apeiron$itemTypesTotal(final ContainerNetworkStatus container) {
        return BigGuiNumberCapture.captureNetworkInteger(8, apeiron$exact(container).getItemTypesTotalBig());
    }

    @Redirect(method = "drawFluidInfo", at = @At(value = "INVOKE", target = "Lappeng/container/implementations/ContainerNetworkStatus;getFluidCellCount()J"))
    private long apeiron$fluidCellCount(final ContainerNetworkStatus container) {
        return BigGuiNumberCapture.captureNetworkInteger(9, apeiron$exact(container).getFluidCellCountBig());
    }

    @Redirect(method = "drawFluidInfo", at = @At(value = "INVOKE", target = "Lappeng/container/implementations/ContainerNetworkStatus;getFluidTypesUsed()J"))
    private long apeiron$fluidTypesUsed(final ContainerNetworkStatus container) {
        return BigGuiNumberCapture.captureNetworkInteger(10, apeiron$exact(container).getFluidTypesUsedBig());
    }

    @Redirect(method = "drawFluidInfo", at = @At(value = "INVOKE", target = "Lappeng/container/implementations/ContainerNetworkStatus;getFluidTypesTotal()J"))
    private long apeiron$fluidTypesTotal(final ContainerNetworkStatus container) {
        return BigGuiNumberCapture.captureNetworkInteger(11, apeiron$exact(container).getFluidTypesTotalBig());
    }

    @Redirect(method = "drawEssentiaInfo", at = @At(value = "INVOKE", target = "Lappeng/container/implementations/ContainerNetworkStatus;getEssentiaCellCount()J"))
    private long apeiron$essentiaCellCount(final ContainerNetworkStatus container) {
        return BigGuiNumberCapture.captureNetworkInteger(12, apeiron$exact(container).getEssentiaCellCountBig());
    }

    @Redirect(method = "drawEssentiaInfo", at = @At(value = "INVOKE", target = "Lappeng/container/implementations/ContainerNetworkStatus;getEssentiaTypesUsed()J"))
    private long apeiron$essentiaTypesUsed(final ContainerNetworkStatus container) {
        return BigGuiNumberCapture.captureNetworkInteger(13, apeiron$exact(container).getEssentiaTypesUsedBig());
    }

    @Redirect(method = "drawEssentiaInfo", at = @At(value = "INVOKE", target = "Lappeng/container/implementations/ContainerNetworkStatus;getEssentiaTypesTotal()J"))
    private long apeiron$essentiaTypesTotal(final ContainerNetworkStatus container) {
        return BigGuiNumberCapture.captureNetworkInteger(14, apeiron$exact(container).getEssentiaTypesTotalBig());
    }

    @Redirect(
        method = "drawItemRepo",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IAEStack;getStackSize()J"))
    private long apeiron$captureStack(final IAEStack<?> stack) {
        return BigGuiNumberCapture.captureStack(stack);
    }

    @Redirect(
        method = "drawItemRepo",
        at = @At(value = "INVOKE", target = "Ljava/lang/Long;toString(J)Ljava/lang/String;"))
    private String apeiron$formatCompact(final long value) {
        return BigGuiNumberCapture.longString(value);
    }

    @Redirect(
        method = "drawItemRepo",
        at = @At(value = "INVOKE", target = "Ljava/text/NumberFormat;format(J)Ljava/lang/String;"))
    private String apeiron$formatExact(final NumberFormat formatter, final long value) {
        return BigGuiNumberCapture.formatExactStack(value);
    }

    @Redirect(
        method = "drawItemRepo",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IAEStack;getCountRequestable()J"))
    private long apeiron$captureRequestable(final IAEStack<?> stack) {
        return BigGuiNumberCapture.captureRequestable(stack);
    }

    @Redirect(
        method = "drawItemRepo",
        at = @At(value = "INVOKE", target = "Lappeng/util/Platform;formatPowerLong(JZ)Ljava/lang/String;"))
    private static String apeiron$formatPower(final long value, final boolean isRate) {
        return BigGuiNumberCapture.formatPower(value, isRate);
    }
}
