package com.silvia.apeiron.mixin.ae.crafting.gui;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.crafting.diagnostics.BigDiagnosticGuiRow;
import com.silvia.apeiron.ae.crafting.diagnostics.BigDiagnosticRow;
import com.silvia.apeiron.math.BigNumberFormatter;

import appeng.me.diagnostics.DiagnosticRowView;

/** Renders client-side crafting diagnostics from the exact packet sidecars. */
@Mixin(targets = "appeng.client.gui.implementations.GuiCraftingDiagnosticTerminal$Row", remap = false)
public abstract class GuiCraftingDiagnosticRowBigMixin implements BigDiagnosticGuiRow {

    private static final int TICKS_PER_SECOND = 20;

    @Shadow
    private appeng.api.storage.data.IAEStack<?> stack;

    @Shadow
    private long totalProduced;

    @Shadow
    private long elapsedTimeTicks;

    @Shadow
    private long sampleCount;

    @Shadow
    private String getDisplayName() {
        throw new AssertionError("mixin shadow was not replaced");
    }

    @Unique
    private BigInteger apeiron$totalProduced;
    @Unique
    private BigInteger apeiron$elapsedTimeTicks;
    @Unique
    private BigInteger apeiron$sampleCount;

    @Inject(method = "fromPacket", at = @At("RETURN"))
    private static void apeiron$capture(final DiagnosticRowView packetRow, final CallbackInfoReturnable<Object> cir) {
        if (!((Object) packetRow instanceof BigDiagnosticRow) || !(cir.getReturnValue() instanceof BigDiagnosticGuiRow)) return;
        final BigDiagnosticRow packet = (BigDiagnosticRow) (Object) packetRow;
        final BigDiagnosticGuiRow row = (BigDiagnosticGuiRow) cir.getReturnValue();
        row.setExactGuiValues(packet.getTotalProducedBig(), packet.getElapsedTimeTicksBig(), packet.getSampleCountBig());
    }

    @Override
    public BigInteger getTotalProducedBig() {
        return this.apeiron$totalProduced == null ? BigInteger.valueOf(this.totalProduced) : this.apeiron$totalProduced;
    }

    @Override
    public BigInteger getElapsedTimeTicksBig() {
        return this.apeiron$elapsedTimeTicks == null
            ? BigInteger.valueOf(this.elapsedTimeTicks)
            : this.apeiron$elapsedTimeTicks;
    }

    @Override
    public BigInteger getSampleCountBig() {
        return this.apeiron$sampleCount == null ? BigInteger.valueOf(this.sampleCount) : this.apeiron$sampleCount;
    }

    @Override
    public double getItemsPerSecondBig() {
        final BigInteger ticks = this.getElapsedTimeTicksBig();
        if (ticks.signum() <= 0) return 0.0D;
        return new BigDecimal(this.getTotalProducedBig())
            .multiply(BigDecimal.valueOf(TICKS_PER_SECOND))
            .divide(new BigDecimal(ticks), 8, RoundingMode.HALF_UP)
            .doubleValue();
    }

    @Override
    public String getDisplayNameForApeiron() {
        return this.getDisplayName();
    }

    @Override
    public void setExactGuiValues(final BigInteger totalProduced, final BigInteger elapsedTimeTicks,
        final BigInteger sampleCount) {
        this.apeiron$totalProduced = totalProduced;
        this.apeiron$elapsedTimeTicks = elapsedTimeTicks;
        this.apeiron$sampleCount = sampleCount;
    }

    @Unique
    private static String apeiron$formatCompact(final BigInteger value) {
        return BigNumberFormatter.formatCompact(value);
    }

    @Overwrite
    private String getCompactProduced() {
        return apeiron$formatCompact(this.getTotalProducedBig());
    }

    @Overwrite
    private double getItemsPerSecond() {
        return this.getItemsPerSecondBig();
    }

    @Overwrite
    private String getCompactFormattedItemsPerSecond(final java.text.DecimalFormat format) {
        if (this.getElapsedTimeTicksBig().signum() <= 0) return "-";
        final double itemsPerSecond = this.getItemsPerSecondBig();
        if (itemsPerSecond >= 1000.0D) {
            if (Double.isFinite(itemsPerSecond) && itemsPerSecond <= Long.MAX_VALUE) {
                return BigNumberFormatter.formatCompact(BigInteger.valueOf(Math.round(itemsPerSecond)));
            }
            return String.format(java.util.Locale.ROOT, "%.2E", itemsPerSecond);
        }
        final long whole = (long) itemsPerSecond;
        return Math.abs(itemsPerSecond - whole) < 0.1D
            ? Long.toString(whole)
            : String.format(java.util.Locale.ROOT, "%.1f", itemsPerSecond);
    }
}
