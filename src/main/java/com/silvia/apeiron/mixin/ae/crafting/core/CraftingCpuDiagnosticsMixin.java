package com.silvia.apeiron.mixin.ae.crafting.core;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import com.silvia.apeiron.ae.crafting.diagnostics.BigCraftingDiagnosticsValues;
import com.silvia.apeiron.ae.crafting.diagnostics.BigCraftingTimingRecord;
import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.api.storage.data.IAEStack;
import appeng.me.cluster.implementations.CraftingCpuDiagnostics;
import appeng.me.diagnostics.CraftingDiagnosticSessionId;

/** Uses exact counts for the diagnostic timing queue and completion accounting. */
@Mixin(value = CraftingCpuDiagnostics.class, remap = false)
public abstract class CraftingCpuDiagnosticsMixin {

    @Shadow
    @Final
    protected Map<IAEStack<?>, NavigableSet<CraftingCpuDiagnostics.CraftingTimingRecord>> outputTimingRecords;

    @Overwrite
    protected void recordExpectedOutput(final IAEStack<?> output, final long outputObservedAtTick,
        final CraftingDiagnosticSessionId diagnosticSessionId) {
        final BigInteger exact = BigAEStackValues.get(output);
        if (output == null || exact.signum() <= 0 || diagnosticSessionId == null) return;
        final IAEStack<?> key = CraftingCpuDiagnostics.normalizeTrackingStack(output);
        if (key == null) return;
        final NavigableSet<CraftingCpuDiagnostics.CraftingTimingRecord> records = this.outputTimingRecords
            .computeIfAbsent(key, ignored -> new TreeSet<>());
        BigCraftingDiagnosticsValues.captureTiming(exact);
        final CraftingCpuDiagnostics.CraftingTimingRecord probe = new CraftingCpuDiagnostics.CraftingTimingRecord(
            BigAEStackValues.saturatedLong(exact),
            outputObservedAtTick,
            diagnosticSessionId);
        final CraftingCpuDiagnostics.CraftingTimingRecord existing = records.ceiling(probe);
        if (existing != null && existing.compareTo(probe) == 0) {
            ((BigCraftingTimingRecord) (Object) existing).addProducedBig(exact);
        } else {
            records.add(probe);
        }
    }

    @Overwrite
    protected List<CraftingCpuDiagnostics.CompletedDiagnosticRecord> recordReturnedOutputs(
        final IAEStack<?> returnedStack) {
        final BigInteger exactReturned = BigAEStackValues.get(returnedStack);
        if (returnedStack == null || exactReturned.signum() <= 0) return Collections.emptyList();
        final IAEStack<?> key = CraftingCpuDiagnostics.normalizeTrackingStack(returnedStack);
        if (key == null) return Collections.emptyList();
        final NavigableSet<CraftingCpuDiagnostics.CraftingTimingRecord> records = this.outputTimingRecords.get(key);
        if (records == null || records.isEmpty()) return Collections.emptyList();

        BigInteger remainingReturned = exactReturned;
        final long endTick = CraftingCpuDiagnostics.getServerTick();
        final List<CraftingCpuDiagnostics.CompletedDiagnosticRecord> completed = new ArrayList<>();
        while (remainingReturned.signum() > 0 && !records.isEmpty()) {
            final CraftingCpuDiagnostics.CraftingTimingRecord record = records.first();
            final BigInteger remaining = BigCraftingTimingRecord.remaining(record);
            final BigInteger consumed = remainingReturned.min(remaining);
            ((BigCraftingTimingRecord) (Object) record).addRemainingToProduceBig(consumed.negate());
            remainingReturned = remainingReturned.subtract(consumed);

            if (BigCraftingTimingRecord.remaining(record)
                .signum() <= 0) {
                ((BigCraftingTimingRecord) (Object) record).setEndTickBig(endTick);
                final BigInteger produced = BigCraftingTimingRecord.original(record);
                BigCraftingDiagnosticsValues.captureCompleted(produced);
                completed.add(
                    new CraftingCpuDiagnostics.CompletedDiagnosticRecord(
                        returnedStack,
                        record.getDiagnosticSessionId(),
                        BigAEStackValues.saturatedLong(produced),
                        record.getStartTick(),
                        record.getEndTick(),
                        record.getElapsedTicks()));
                records.pollFirst();
            }
        }
        if (records.isEmpty()) this.outputTimingRecords.remove(key);
        return completed;
    }
}
