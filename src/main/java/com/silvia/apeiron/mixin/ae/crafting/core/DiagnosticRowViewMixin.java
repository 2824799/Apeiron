package com.silvia.apeiron.mixin.ae.crafting.core;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.crafting.diagnostics.BigDiagnosticRow;
import com.silvia.apeiron.ae.crafting.diagnostics.BigDiagnosticRowValues;

import appeng.me.diagnostics.DiagnosticRowView;

/** Keeps diagnostic rows exact while retaining AE's public long fields. */
@Mixin(value = DiagnosticRowView.class, remap = false)
public abstract class DiagnosticRowViewMixin implements BigDiagnosticRow {

    @Unique
    private BigInteger apeiron$totalProduced;
    @Unique
    private BigInteger apeiron$elapsedTimeTicks;
    @Unique
    private BigInteger apeiron$sampleCount;

    @Inject(method = "<init>(Lappeng/api/storage/data/IAEStack;JJJ)V", at = @At("HEAD"))
    private void apeiron$capture(final appeng.api.storage.data.IAEStack<?> stack, final long totalProduced,
        final long elapsedTimeTicks, final long sampleCount, final CallbackInfo ci) {
        final BigInteger[] values = BigDiagnosticRowValues.take();
        this.apeiron$totalProduced = values == null ? BigInteger.valueOf(totalProduced) : values[0];
        this.apeiron$elapsedTimeTicks = values == null ? BigInteger.valueOf(elapsedTimeTicks) : values[1];
        this.apeiron$sampleCount = values == null ? BigInteger.valueOf(sampleCount) : values[2];
    }

    @Override
    public BigInteger getTotalProducedBig() {
        return this.apeiron$totalProduced == null
            ? BigInteger.valueOf(((DiagnosticRowView) (Object) this).totalProduced)
            : this.apeiron$totalProduced;
    }

    @Override
    public BigInteger getElapsedTimeTicksBig() {
        return this.apeiron$elapsedTimeTicks == null
            ? BigInteger.valueOf(((DiagnosticRowView) (Object) this).elapsedTimeTicks)
            : this.apeiron$elapsedTimeTicks;
    }

    @Override
    public BigInteger getSampleCountBig() {
        return this.apeiron$sampleCount == null
            ? BigInteger.valueOf(((DiagnosticRowView) (Object) this).sampleCount)
            : this.apeiron$sampleCount;
    }

    @Override
    public void setExactValues(final BigInteger totalProduced, final BigInteger elapsedTimeTicks,
        final BigInteger sampleCount) {
        this.apeiron$totalProduced = totalProduced;
        this.apeiron$elapsedTimeTicks = elapsedTimeTicks;
        this.apeiron$sampleCount = sampleCount;
    }
}
