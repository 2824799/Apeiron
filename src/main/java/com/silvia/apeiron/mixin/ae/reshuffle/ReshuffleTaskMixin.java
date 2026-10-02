package com.silvia.apeiron.mixin.ae.reshuffle;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.reshuffle.BigReshuffleObjects;
import com.silvia.apeiron.ae.reshuffle.BigReshufflePending;
import com.silvia.apeiron.ae.reshuffle.BigReshuffleReportAccess;
import com.silvia.apeiron.ae.reshuffle.BigReshuffleSource;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigMEInventories;

import appeng.api.config.Actionable;
import appeng.api.config.ReshufflePhase;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.security.ReshuffleActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.helpers.ReshuffleReport;
import appeng.helpers.ReshuffleTask;

/** Exact transfer and rollback accounting while retaining AE's subnet traversal and per-tick batches. */
@SuppressWarnings({ "rawtypes", "unchecked" })
@Mixin(value = ReshuffleTask.class, remap = false)
public abstract class ReshuffleTaskMixin {

    @Shadow
    @Final
    private ReshuffleActionSource src;
    @Shadow
    @Final
    private MachineSource rollbackSrc;
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
    private List injectQueue;
    @Shadow
    @Final
    private Map injectLookup;
    @Shadow
    private Iterator injectIterator;

    @Unique
    private BigInteger apeiron$extractedBig = BigInteger.ZERO;
    @Unique
    private BigInteger apeiron$injectedBig = BigInteger.ZERO;

    @Inject(method = "lambda$handlerProcessor$0", at = @At("HEAD"), cancellable = true)
    private void apeiron$extractEntry(final boolean extract, final IMEInventoryHandler handler,
        final IItemList<IAEStack<?>> target, final Object value, final CallbackInfo ci) {
        if (!extract || !(value instanceof IAEStack<?>)) return;
        final IAEStack<?> request = ((IAEStack<?>) value).copy();
        final BigInteger requested = BigAEStackValues.get(request);
        final IAEStack<?> result = BigMEInventories.extractItemsBig(handler, request, Actionable.MODULATE, this.src);
        if (result == null) {
            this.cantExtract.add(request);
        } else {
            final BigInteger amount = BigAEStackValues.get(result);
            com.silvia.apeiron.ae.storage.BigInventoryAdaptors.checkReturnedAmount(amount, requested);
            if (amount.compareTo(requested) < 0) {
                this.cantExtract.add(BigAEStackValues.copyWithSize(request, requested.subtract(amount)));
            }
            this.extractedItems += amount.doubleValue();
            this.apeiron$extractedBig = this.apeiron$extractedBig.add(amount);
            target.add(result);
            BigReshufflePending pending = (BigReshufflePending) this.injectLookup.get(result);
            if (pending == null) {
                pending = BigReshuffleObjects.pending(target.findPrecise(result));
                this.injectLookup.put(pending.getStackBig(), pending);
                this.injectQueue.add(pending);
            }
            pending.addSourceBig(handler, amount);
            this.extractedTypes = this.extracted.size();
        }
        ci.cancel();
    }

    @Inject(method = "toQueueList", at = @At("TAIL"))
    private void apeiron$sortExact(final CallbackInfo ci) {
        this.injectQueue.sort((left, right) -> {
            final int order = BigAEStackValues
                .compare(((BigReshufflePending) left).getStackBig(), ((BigReshufflePending) right).getStackBig());
            return this.insertOrder ? -order : order;
        });
    }

    @Unique
    private void apeiron$returnPending(final BigReshufflePending pending) {
        final IAEStack<?> stack = pending.getStackBig();
        if (stack == null) return;
        BigInteger remaining = BigAEStackValues.get(stack);
        for (Object raw : pending.getSourcesBig()) {
            if (remaining.signum() <= 0) break;
            final BigReshuffleSource source = (BigReshuffleSource) raw;
            final BigInteger amount = remaining.min(source.getAmountBig());
            final IAEStack<?> offered = BigAEStackValues.copyWithSize(stack, amount);
            IAEStack<?> rejected = offered;
            try {
                rejected = BigMEInventories
                    .injectItemsBig(source.getSourceBig(), offered, Actionable.MODULATE, this.rollbackSrc);
            } catch (Exception error) {
                appeng.core.AELog.error(error, "Failed to restore a storage reshuffle stack to its source");
            }
            if (rejected != null) this.cantInject.add(rejected);
            remaining = remaining.subtract(amount);
        }
        if (remaining.signum() > 0) this.cantInject.add(BigAEStackValues.copyWithSize(stack, remaining));
        pending.setStackBig(null);
    }

    @Inject(method = "returnPendingItem", at = @At("HEAD"), cancellable = true)
    private void apeiron$restoreExact(@Coerce final Object entry, final CallbackInfo ci) {
        this.apeiron$returnPending((BigReshufflePending) entry);
        ci.cancel();
    }

    @Inject(method = "processNextBatch", at = @At("HEAD"), cancellable = true)
    private void apeiron$injectBatch(final CallbackInfo ci) {
        if (this.phase != ReshufflePhase.INJECTION) return;
        int operations = 0;
        if (this.injectIterator == null) this.injectIterator = this.injectQueue.iterator();
        while (this.injectIterator.hasNext()) {
            final BigReshufflePending pending = (BigReshufflePending) this.injectIterator.next();
            final IAEStack<?> stack = pending.getStackBig();
            if (stack == null) {
                this.injectIterator.remove();
                continue;
            }
            final BigInteger offered = BigAEStackValues.get(stack);
            final IMEMonitor monitor = this.sg.getMEMonitor(stack.getStackType());
            final IAEStack<?> result = monitor == null ? stack
                : BigMEInventories.injectItemsBig(monitor, stack.copy(), Actionable.MODULATE, this.src);
            final BigInteger rejected = BigAEStackValues.get(result);
            com.silvia.apeiron.ae.storage.BigInventoryAdaptors.checkReturnedAmount(rejected, offered);
            pending.setStackBig(result);
            if (result != null) this.apeiron$returnPending(pending);
            final BigInteger inserted = offered.subtract(rejected);
            if (inserted.signum() > 0) this.injectedTypes++;
            this.injectedItems += inserted.doubleValue();
            this.apeiron$injectedBig = this.apeiron$injectedBig.add(inserted);
            this.injectIterator.remove();
            if (++operations == ReshuffleTask.stacks_per_tick) {
                ci.cancel();
                return;
            }
        }
        this.injectIterator = null;
        this.phase = ReshufflePhase.AFTER_SNAPSHOT;
        ci.cancel();
    }

    @Inject(method = "getReport", at = @At("RETURN"))
    private void apeiron$attachExactReport(final CallbackInfoReturnable<ReshuffleReport> cir) {
        final ReshuffleReport report = cir.getReturnValue();
        if (report instanceof BigReshuffleReportAccess access) {
            access.setTransferTotalsBig(this.apeiron$extractedBig, this.apeiron$injectedBig);
        }
    }
}
