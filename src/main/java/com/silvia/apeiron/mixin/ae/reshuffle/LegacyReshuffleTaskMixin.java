package com.silvia.apeiron.mixin.ae.reshuffle;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.reshuffle.BigReshuffleReportAccess;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigMEInventories;

import appeng.api.config.Actionable;
import appeng.api.config.ReshufflePhase;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.security.ReshuffleActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.helpers.ReshuffleReport;
import appeng.helpers.ReshuffleTask;

/** Exact transfers and source-specific rollback for AE2 before its native contribution ledger. */
@SuppressWarnings({ "rawtypes", "unchecked" })
@Mixin(value = ReshuffleTask.class, remap = false)
public abstract class LegacyReshuffleTaskMixin {

    @Shadow
    @Final
    private BaseActionSource src;
    @Shadow
    @Final
    private IStorageGrid sg;
    @Shadow
    @Final
    private boolean insertOrder;
    @Shadow
    private ReshufflePhase phase;
    @Shadow
    private int extractedTypes;
    @Shadow
    private int injectedTypes;
    @Shadow
    private double extractedItems;
    @Shadow
    private double injectedItems;
    @Shadow
    @Final
    private IItemList<IAEStack<?>> extracted;
    @Shadow
    @Final
    private IItemList<IAEStack<?>> cantExtract;
    @Shadow
    @Final
    private IItemList<IAEStack<?>> cantInject;
    @Shadow
    @Final
    private List<IAEStack<?>> injectQueue;
    @Shadow
    private Iterator<IAEStack<?>> injectIterator;

    @Unique
    private final Map<IAEStack<?>, List<Contribution>> apeiron$sources = new HashMap<>();
    @Unique
    private BigInteger apeiron$extracted = BigInteger.ZERO;
    @Unique
    private BigInteger apeiron$injected = BigInteger.ZERO;

    @Unique
    private static final class Contribution {

        final IMEInventoryHandler source;
        BigInteger amount;

        Contribution(IMEInventoryHandler source, BigInteger amount) {
            this.source = source;
            this.amount = amount;
        }
    }

    @Inject(method = "lambda$handlerProcessor$0", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$extract(boolean extract, IMEInventoryHandler handler, IItemList<IAEStack<?>> target,
        Object value, CallbackInfo ci) {
        if (!extract || !(value instanceof IAEStack)) return;
        IAEStack<?> request = ((IAEStack<?>) value).copy();
        BigInteger requested = BigAEStackValues.get(request);
        IAEStack<?> result = BigMEInventories.extractItemsBig(handler, request, Actionable.MODULATE, src);
        BigInteger taken = BigAEStackValues.get(result);
        com.silvia.apeiron.ae.storage.BigInventoryAdaptors.checkReturnedAmount(taken, requested);
        if (taken.compareTo(requested) < 0)
            cantExtract.add(BigAEStackValues.copyWithSize(request, requested.subtract(taken)));
        if (taken.signum() > 0) {
            target.add(result);
            apeiron$sources.computeIfAbsent(request, key -> new ArrayList<>())
                .add(new Contribution(handler, taken));
            apeiron$extracted = apeiron$extracted.add(taken);
            extractedItems += taken.doubleValue();
            extractedTypes = extracted.size();
        }
        ci.cancel();
    }

    @Inject(method = "toQueueList", at = @At("TAIL"), require = 1)
    private void apeiron$sort(CallbackInfo ci) {
        injectQueue.sort(
            (left, right) -> insertOrder ? -BigAEStackValues.compare(left, right)
                : BigAEStackValues.compare(left, right));
    }

    @Inject(method = "processNextBatch", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$inject(CallbackInfo ci) {
        if (phase != ReshufflePhase.INJECTION) return;
        if (injectIterator == null) injectIterator = injectQueue.iterator();
        int operations = 0;
        while (injectIterator.hasNext()) {
            IAEStack<?> stack = injectIterator.next();
            BigInteger offered = BigAEStackValues.get(stack);
            IMEMonitor monitor = sg.getMEMonitor(stack.getStackType());
            IAEStack<?> rejected;
            try {
                rejected = monitor == null ? stack
                    : BigMEInventories.injectItemsBig(monitor, stack.copy(), Actionable.MODULATE, src);
            } catch (RuntimeException failure) {
                apeiron$restore(stack);
                injectIterator.remove();
                throw failure;
            }
            BigInteger remainder = BigAEStackValues.get(rejected);
            com.silvia.apeiron.ae.storage.BigInventoryAdaptors.checkReturnedAmount(remainder, offered);
            BigInteger inserted = offered.subtract(remainder);
            apeiron$consumeSources(stack, inserted);
            if (rejected != null) apeiron$restore(rejected);
            else apeiron$sources.remove(stack);
            if (inserted.signum() > 0) injectedTypes++;
            apeiron$injected = apeiron$injected.add(inserted);
            injectedItems += inserted.doubleValue();
            injectIterator.remove();
            if (++operations == ReshuffleTask.stacks_per_tick) {
                ci.cancel();
                return;
            }
        }
        injectIterator = null;
        phase = ReshufflePhase.AFTER_SNAPSHOT;
        ci.cancel();
    }

    @Unique
    private void apeiron$consumeSources(IAEStack<?> stack, BigInteger inserted) {
        List<Contribution> sources = apeiron$sources.get(stack);
        if (sources == null) return;
        for (Contribution contribution : sources) {
            BigInteger consumed = contribution.amount.min(inserted);
            contribution.amount = contribution.amount.subtract(consumed);
            inserted = inserted.subtract(consumed);
            if (inserted.signum() == 0) return;
        }
    }

    @Unique
    private void apeiron$restore(IAEStack<?> stack) {
        BigInteger remaining = BigAEStackValues.get(stack);
        List<Contribution> sources = apeiron$sources.remove(stack);
        BaseActionSource rollback = src instanceof ReshuffleActionSource
            ? new MachineSource(((ReshuffleActionSource) src).via)
            : src;
        if (sources != null) for (Contribution contribution : sources) {
            BigInteger amount = remaining.min(contribution.amount);
            if (amount.signum() == 0) continue;
            IAEStack<?> offered = BigAEStackValues.copyWithSize(stack, amount);
            IAEStack<?> rejected = offered;
            try {
                rejected = BigMEInventories.injectItemsBig(contribution.source, offered, Actionable.MODULATE, rollback);
            } catch (RuntimeException failure) {
                appeng.core.AELog.error(failure, "Failed to restore a legacy storage reshuffle stack");
            }
            if (rejected != null) cantInject.add(rejected);
            remaining = remaining.subtract(amount);
        }
        if (remaining.signum() > 0) cantInject.add(BigAEStackValues.copyWithSize(stack, remaining));
    }

    @Inject(method = "returnPendingItems", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$rollback(Iterator<IAEStack<?>> iterator, CallbackInfo ci) {
        while (iterator.hasNext()) {
            apeiron$restore(iterator.next());
            iterator.remove();
        }
        ci.cancel();
    }

    @Inject(method = "error", at = @At("HEAD"), require = 1)
    private void apeiron$restoreError(CallbackInfo ci) {
        Iterator<IAEStack<?>> pending = extracted.iterator();
        while (pending.hasNext()) {
            apeiron$restore(pending.next());
            pending.remove();
        }
        pending = injectQueue.iterator();
        while (pending.hasNext()) {
            apeiron$restore(pending.next());
            pending.remove();
        }
        injectIterator = null;
    }

    @Inject(method = "getReport", at = @At("RETURN"), require = 1)
    private void apeiron$report(CallbackInfoReturnable<ReshuffleReport> cir) {
        ((BigReshuffleReportAccess) cir.getReturnValue()).setTransferTotalsBig(apeiron$extracted, apeiron$injected);
    }
}
