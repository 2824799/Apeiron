package com.silvia.apeiron.mixin.ae.reshuffle;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.reshuffle.BigReshuffleItemChangeAccess;
import com.silvia.apeiron.ae.reshuffle.BigReshuffleReportAccess;
import com.silvia.apeiron.ae.reshuffle.BigScanRecord;
import com.silvia.apeiron.ae.terminal.BigGuiNumberCapture;

import appeng.api.config.HealthSortOrder;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.implementations.GuiStorageReshuffle;
import appeng.container.implementations.ContainerStorageReshuffle;
import appeng.core.localization.Localization;
import appeng.helpers.ReshuffleReport;
import appeng.helpers.ReshuffleReport.ItemChange;
import appeng.helpers.ScanTask.ScanRecord;

/** Displays exact storage-reshuffle counts in the report and tooltips. */
@Mixin(value = GuiStorageReshuffle.class, remap = false)
public abstract class GuiStorageReshuffleBigMixin {

    @Shadow
    @Final
    private ContainerStorageReshuffle container;
    @Shadow
    @Final
    private java.util.List<ScanRecord> scanRecords;

    @Inject(method = "drawScanContent", at = @At("HEAD"))
    private void apeiron$clearScanCapture(final int mouseX, final int mouseY, final CallbackInfo ci) {
        BigGuiNumberCapture.clearScanValues();
    }

    @Overwrite
    private double fillPct(final ScanRecord record) {
        if (!(record instanceof BigScanRecord)) {
            return record.bytesTotal > 0 ? record.bytesUsed * 100.0 / record.bytesTotal : 0.0;
        }
        BigScanRecord exact = (BigScanRecord) (Object) record;
        BigInteger total = exact.getBytesTotalBig();
        if (total.signum() <= 0) return 0.0;
        return new BigDecimal(exact.getBytesUsedBig()).multiply(BigDecimal.valueOf(100L))
            .divide(new BigDecimal(total), 8, java.math.RoundingMode.HALF_UP)
            .doubleValue();
    }

    @Overwrite
    private void sortHealthEntries() {
        final HealthSortOrder order = this.container.healthSortOrder;
        final int dir = appeng.api.config.SortDir.ASCENDING == this.container.healthSortDir ? 1 : -1;
        this.scanRecords.sort((left, right) -> {
            final BigScanRecord a = left instanceof BigScanRecord ? (BigScanRecord) (Object) left : null;
            final BigScanRecord b = right instanceof BigScanRecord ? (BigScanRecord) (Object) right : null;
            final int comparison;
            if (order == HealthSortOrder.FILL_PCT) {
                final double av = fillPct(left);
                final double bv = fillPct(right);
                comparison = Double.compare(bv, av);
            } else {
                final BigInteger av = a == null ? BigInteger.valueOf(left.bytesTotal) : a.getBytesTotalBig();
                final BigInteger bv = b == null ? BigInteger.valueOf(right.bytesTotal) : b.getBytesTotalBig();
                comparison = bv.compareTo(av);
            }
            return dir * comparison;
        });
    }

    @Redirect(
        method = "drawScanContent",
        at = @At(value = "FIELD", target = "Lappeng/helpers/ScanTask$ScanRecord;typesUsed:J"))
    private long apeiron$scanTypesUsed(final ScanRecord record) {
        BigInteger value = record instanceof BigScanRecord ? ((BigScanRecord) (Object) record).getTypesUsedBig()
            : BigInteger.valueOf(record.typesUsed);
        return BigGuiNumberCapture.captureScanTypesUsed(value);
    }

    @Redirect(
        method = "drawScanContent",
        at = @At(value = "FIELD", target = "Lappeng/helpers/ScanTask$ScanRecord;typesTotal:J"))
    private long apeiron$scanTypesTotal(final ScanRecord record) {
        BigInteger value = record instanceof BigScanRecord ? ((BigScanRecord) (Object) record).getTypesTotalBig()
            : BigInteger.valueOf(record.typesTotal);
        return BigGuiNumberCapture.captureScanTypesTotal(value);
    }

    @Redirect(
        method = "drawScanContent",
        at = @At(value = "FIELD", target = "Lappeng/helpers/ScanTask$ScanRecord;bytesUsed:J"))
    private long apeiron$scanBytesUsed(final ScanRecord record) {
        BigInteger value = record instanceof BigScanRecord ? ((BigScanRecord) (Object) record).getBytesUsedBig()
            : BigInteger.valueOf(record.bytesUsed);
        return BigGuiNumberCapture.captureScanBytesUsed(value);
    }

    @Redirect(
        method = "drawScanContent",
        at = @At(value = "FIELD", target = "Lappeng/helpers/ScanTask$ScanRecord;bytesTotal:J"))
    private long apeiron$scanBytesTotal(final ScanRecord record) {
        BigInteger value = record instanceof BigScanRecord ? ((BigScanRecord) (Object) record).getBytesTotalBig()
            : BigInteger.valueOf(record.bytesTotal);
        return BigGuiNumberCapture.captureScanBytesTotal(value);
    }

    @Redirect(
        method = "drawScanContent",
        at = @At(value = "INVOKE", target = "Ljava/lang/StringBuilder;append(J)Ljava/lang/StringBuilder;"))
    private static StringBuilder apeiron$formatScanInteger(final StringBuilder builder, final long value) {
        return builder.append(BigGuiNumberCapture.formatScanInteger(value));
    }

    @Redirect(
        method = "drawScanContent",
        at = @At(value = "INVOKE", target = "Lappeng/util/Platform;formatByteDouble(D)Ljava/lang/String;"))
    private static String apeiron$formatScanBytes(final double value) {
        return BigGuiNumberCapture.formatScanBytes(value);
    }

    @Redirect(
        method = "drawScanContent",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/core/localization/Localization;getLocal([Ljava/lang/Object;)Ljava/lang/String;"))
    private static String apeiron$formatScanTypes(final Localization text, final Object[] args) {
        return text.getLocal(BigGuiNumberCapture.formatScanTypeArgs(args));
    }

    @Redirect(
        method = "generateReportLines",
        at = @At(value = "FIELD", target = "Lappeng/helpers/ReshuffleReport;extractedItems:D"))
    private double apeiron$capturedExtracted(final ReshuffleReport report) {
        final BigReshuffleReportAccess access = (BigReshuffleReportAccess) report;
        return BigGuiNumberCapture.captureReport(access.getExtractedItemsBig(), report.extractedItems);
    }

    @Redirect(
        method = "generateReportLines",
        at = @At(value = "FIELD", target = "Lappeng/helpers/ReshuffleReport;injectedItems:D"))
    private double apeiron$capturedInjected(final ReshuffleReport report) {
        final BigReshuffleReportAccess access = (BigReshuffleReportAccess) report;
        return BigGuiNumberCapture.captureReport(access.getInjectedItemsBig(), report.injectedItems);
    }

    @Redirect(
        method = "generateReportLines",
        at = @At(value = "FIELD", target = "Lappeng/helpers/ReshuffleReport;beforeItems:D"))
    private double apeiron$capturedBefore(final ReshuffleReport report) {
        final BigReshuffleReportAccess access = (BigReshuffleReportAccess) report;
        return BigGuiNumberCapture.captureReport(access.getBeforeItemsBig(), report.beforeItems);
    }

    @Redirect(
        method = "generateReportLines",
        at = @At(value = "FIELD", target = "Lappeng/helpers/ReshuffleReport;afterItems:D"))
    private double apeiron$capturedAfter(final ReshuffleReport report) {
        final BigReshuffleReportAccess access = (BigReshuffleReportAccess) report;
        return BigGuiNumberCapture.captureReport(access.getAfterItemsBig(), report.afterItems);
    }

    @Redirect(
        method = { "generateReportLines", "addItemList", "buildItemLines" },
        at = @At(value = "INVOKE", target = "Lappeng/util/Platform;fmt(D)Ljava/lang/String;"))
    private static String apeiron$formatReport(final double value) {
        return BigGuiNumberCapture.formatReport(value);
    }

    @Redirect(
        method = "addItemList",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IAEStack;getStackSize()J"))
    private long apeiron$captureStack(final IAEStack<?> stack) {
        return BigGuiNumberCapture.captureStack(stack);
    }

    @Redirect(
        method = "buildItemLines",
        at = @At(value = "FIELD", target = "Lappeng/helpers/ReshuffleReport$ItemChange;difference:J"))
    private long apeiron$captureDifference(final ItemChange change) {
        return BigGuiNumberCapture
            .captureReportLong(((BigReshuffleItemChangeAccess) change).getDifferenceBig(), change.difference);
    }

    @Redirect(
        method = "buildItemLines",
        at = @At(value = "FIELD", target = "Lappeng/helpers/ReshuffleReport$ItemChange;beforeCount:J"))
    private long apeiron$captureBefore(final ItemChange change) {
        return BigGuiNumberCapture
            .captureReportLong(((BigReshuffleItemChangeAccess) change).getBeforeCountBig(), change.beforeCount);
    }

    @Redirect(
        method = "buildItemLines",
        at = @At(value = "FIELD", target = "Lappeng/helpers/ReshuffleReport$ItemChange;afterCount:J"))
    private long apeiron$captureAfter(final ItemChange change) {
        return BigGuiNumberCapture
            .captureReportLong(((BigReshuffleItemChangeAccess) change).getAfterCountBig(), change.afterCount);
    }
}
