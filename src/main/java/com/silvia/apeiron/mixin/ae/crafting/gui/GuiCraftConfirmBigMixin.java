package com.silvia.apeiron.mixin.ae.crafting.gui;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.Comparator;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.crafting.core.BigCraftingConfirmation;
import com.silvia.apeiron.ae.crafting.core.UnlimitedCraftingSelection;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.terminal.BigGuiNumberCapture;
import com.silvia.apeiron.math.BigNumberFormatter;

import appeng.api.config.CraftingSortOrder;
import appeng.api.config.SortDir;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.client.gui.implementations.GuiCraftConfirm;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.util.ReadableNumberConverter;

/** Keeps crafting confirmation totals, sorting, labels, and tooltips exact. */
@Mixin(value = GuiCraftConfirm.class, remap = false)
public abstract class GuiCraftConfirmBigMixin {

    @Shadow
    @Final
    private ContainerCraftConfirm ccc;

    @Redirect(
        method = "drawFG",
        at = @At(value = "INVOKE", target = "Lappeng/util/Platform;formatByteDouble(D)Ljava/lang/String;", ordinal = 0),
        require = 1)
    private String apeiron$exactPlanBytes(double legacy) {
        final BigInteger bytes = ((BigCraftingConfirmation) ccc).getUsedBytesBig();
        return BigAEStackValues.fitsLong(bytes) ? appeng.util.Platform.formatByteDouble(legacy)
            : BigNumberFormatter.formatBytes(bytes);
    }

    @Shadow
    @Final
    private IItemList<IAEStack<?>> storage;

    @Shadow
    @Final
    private IItemList<IAEStack<?>> pending;

    @Shadow
    @Final
    private IItemList<IAEStack<?>> missing;

    @Shadow
    private CraftingSortOrder sortMode;

    @Shadow
    private SortDir sortDir;

    @Shadow
    private Comparator<IAEStack<?>> comparator;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void apeiron$installExactComparator(final CallbackInfo ci) {
        this.comparator = (left, right) -> {
            final IAEStack<?> storageLeft = this.storage.findPrecise(left);
            final IAEStack<?> storageRight = this.storage.findPrecise(right);
            final IAEStack<?> pendingLeft = this.pending.findPrecise(left);
            final IAEStack<?> pendingRight = this.pending.findPrecise(right);
            final IAEStack<?> missingLeft = this.missing.findPrecise(left);
            final IAEStack<?> missingRight = this.missing.findPrecise(right);

            if (missingLeft != null && missingRight == null) return -1;
            if (missingLeft == null && missingRight != null) return 1;

            final int direction = this.sortDir.sortHint;
            if (this.sortMode == CraftingSortOrder.CRAFTS) {
                return BigAEStackValues.getCountRequestableCrafts(pendingLeft)
                    .compareTo(BigAEStackValues.getCountRequestableCrafts(pendingRight)) * direction;
            }
            if (this.sortMode == CraftingSortOrder.AMOUNT) {
                return apeiron$total(storageLeft, pendingLeft, missingLeft)
                    .compareTo(apeiron$total(storageRight, pendingRight, missingRight)) * direction;
            }
            if (this.sortMode == CraftingSortOrder.NAME) {
                return left.getDisplayName()
                    .compareToIgnoreCase(right.getDisplayName()) * direction;
            }
            if (this.sortMode == CraftingSortOrder.MOD) {
                final int value = left.getModId()
                    .compareToIgnoreCase(right.getModId());
                return (value == 0 ? left.getDisplayName()
                    .compareToIgnoreCase(right.getDisplayName()) : value) * direction;
            }
            if (this.sortMode == CraftingSortOrder.PERCENT) {
                final float percentLeft = storageLeft != null && pendingLeft == null && missingLeft == null
                    ? storageLeft.getUsedPercent()
                    : -1;
                final float percentRight = storageRight != null && pendingRight == null && missingRight == null
                    ? storageRight.getUsedPercent()
                    : -1;
                return Float.compare(percentLeft, percentRight) * direction;
            }
            return 0;
        };
    }

    @Inject(method = "drawListFG", at = @At("HEAD"))
    private void apeiron$clearDrawCapture(final CallbackInfo ci) {
        BigGuiNumberCapture.clear();
        apeiron$formattingCpuCount = false;
    }

    @Redirect(
        method = "drawListFG",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IAEStack;getStackSize()J"))
    private long apeiron$captureDrawStack(final IAEStack<?> stack) {
        return BigGuiNumberCapture.captureStack(stack);
    }

    @Redirect(
        method = "drawListFG",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IAEStack;getCountRequestableCrafts()J"))
    private long apeiron$captureDrawCrafts(final IAEStack<?> stack) {
        return BigGuiNumberCapture.captureCrafts(stack);
    }

    @Redirect(
        method = "drawListFG",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/util/ReadableNumberConverter;toWideReadableForm(J)Ljava/lang/String;"))
    private String apeiron$formatWide(final ReadableNumberConverter converter, final long value) {
        return BigGuiNumberCapture.formatWideAny(value);
    }

    @Unique
    private boolean apeiron$formattingCpuCount;

    @Redirect(
        method = "drawListFG",
        at = @At(value = "INVOKE", target = "Lappeng/util/Platform;formatByteDouble(D)Ljava/lang/String;"))
    private String apeiron$selectedStorage(double bytes) {
        return ((UnlimitedCraftingSelection) ccc).isSelectedStorageUnlimited() ? "∞"
            : appeng.util.Platform.formatByteDouble(bytes);
    }

    @Redirect(
        method = "drawListFG",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/container/implementations/ContainerCraftConfirm;getCpuCoProcessors()I"))
    private int apeiron$selectedCores(ContainerCraftConfirm container) {
        apeiron$formattingCpuCount = true;
        return container.getCpuCoProcessors();
    }

    @Redirect(
        method = "drawListFG",
        at = @At(value = "INVOKE", target = "Ljava/text/NumberFormat;format(J)Ljava/lang/String;"))
    private String apeiron$formatExact(final NumberFormat formatter, final long value) {
        if (apeiron$formattingCpuCount) {
            apeiron$formattingCpuCount = false;
            return ((UnlimitedCraftingSelection) ccc).isSelectedParallelUnlimited() ? "∞" : formatter.format(value);
        }
        return BigGuiNumberCapture.formatExactAny(value);
    }

    @Inject(method = "getTotal", at = @At("HEAD"))
    private void apeiron$beginTotal(final IAEStack<?> stack, final CallbackInfoReturnable<Long> cir) {
        BigGuiNumberCapture.beginTotal();
    }

    @Redirect(
        method = "getTotal",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IAEStack;getStackSize()J"))
    private long apeiron$addTotal(final IAEStack<?> stack) {
        return BigGuiNumberCapture.addToTotal(stack);
    }

    @Inject(method = "getTotal", at = @At("RETURN"), cancellable = true)
    private void apeiron$finishTotal(final IAEStack<?> stack, final CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(BigGuiNumberCapture.finishTotal(cir.getReturnValue()));
    }

    @Redirect(
        method = "postUpdate",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IAEStack;setStackSize(J)Lappeng/api/storage/data/IAEStack;"))
    private IAEStack<?> apeiron$setTotal(final IAEStack<?> stack, final long value) {
        return BigGuiNumberCapture.setPendingTotal(stack, value);
    }

    @Redirect(
        method = "handleInput",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IAEStack;getStackSize()J"))
    private long apeiron$captureInput(final IAEStack<?> stack) {
        return BigGuiNumberCapture.captureStack(stack);
    }

    @Redirect(
        method = "handleInput",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IAEStack;setStackSize(J)Lappeng/api/storage/data/IAEStack;"))
    private IAEStack<?> apeiron$setInput(final IAEStack<?> stack, final long value) {
        return BigGuiNumberCapture.setCapturedStack(stack, value);
    }

    @Unique
    private static BigInteger apeiron$total(final IAEStack<?> a, final IAEStack<?> b, final IAEStack<?> c) {
        return BigAEStackValues.get(a)
            .add(BigAEStackValues.get(b))
            .add(BigAEStackValues.get(c));
    }
}
