package com.silvia.apeiron.mixin.ae.crafting.core;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.crafting.diagnostics.BigCompletedDiagnosticRecord;
import com.silvia.apeiron.ae.crafting.diagnostics.BigCraftingDiagnosticsValues;
import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.api.storage.data.IAEStack;
import appeng.me.cluster.implementations.CraftingCpuDiagnostics;
import appeng.me.diagnostics.CraftingDiagnosticSessionId;

/** Keeps a completed diagnostic record's produced amount exact. */
@Mixin(value = CraftingCpuDiagnostics.CompletedDiagnosticRecord.class, remap = false)
public abstract class CompletedDiagnosticRecordMixin implements BigCompletedDiagnosticRecord {

    @Unique
    private BigInteger apeiron$produced;

    @Inject(
        method = "<init>(Lappeng/api/storage/data/IAEStack;Lappeng/me/diagnostics/CraftingDiagnosticSessionId;JJJJ)V",
        at = @At("TAIL"))
    private void apeiron$capture(final IAEStack<?> output, final CraftingDiagnosticSessionId session,
        final long produced, final long startTick, final long endTick, final long elapsedTicks, final CallbackInfo ci) {
        this.apeiron$produced = BigCraftingDiagnosticsValues.takeCompleted(produced);
    }

    @Override
    public BigInteger getProducedAmountBig() {
        return this.apeiron$produced == null
            ? BigInteger.valueOf(((CraftingCpuDiagnostics.CompletedDiagnosticRecord) (Object) this).getProducedAmount())
            : this.apeiron$produced;
    }

    @Inject(method = "getProducedAmount", at = @At("RETURN"), cancellable = true)
    private void apeiron$saturate(
        final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Long> cir) {
        if (this.apeiron$produced != null) {
            cir.setReturnValue(BigAEStackValues.saturatedLong(this.apeiron$produced));
        }
    }
}
