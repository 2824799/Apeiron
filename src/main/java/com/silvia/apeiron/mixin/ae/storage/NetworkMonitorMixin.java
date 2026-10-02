package com.silvia.apeiron.mixin.ae.storage;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEItemStacks;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigIMEInventory;
import com.silvia.apeiron.ae.storage.BigMEInventories;
import com.silvia.apeiron.ae.storage.BigMEInventory;
import com.silvia.apeiron.ae.storage.BigStorageInterceptor;
import com.silvia.apeiron.ae.storage.UnsupportedBigInventoryException;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.ReshuffleActionSource;
import appeng.api.networking.storage.IStorageInterceptor;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.me.cache.ItemFlowGridCache;
import appeng.me.cache.NetworkMonitor;

/** Forward exact input/output through the live network monitor and publish exact change deltas. */
@Mixin(value = NetworkMonitor.class, remap = false)
public abstract class NetworkMonitorMixin implements BigIMEInventory, BigMEInventory {

    @Shadow
    private volatile boolean locked;
    @Shadow
    private int localDepthSemaphore;
    @Shadow
    @Final
    private Set<IStorageInterceptor> storageInterceptors;

    @Shadow(remap = false)
    private void postChangesToListeners(Iterable<IAEStack<?>> changes, BaseActionSource source) {
        throw new AssertionError("mixin shadow was not replaced");
    }

    @SuppressWarnings("unchecked")
    private IMEInventoryHandler<IAEItemStack> apeiron$handler() {
        return (IMEInventoryHandler<IAEItemStack>) (Object) ((NetworkMonitor<?>) (Object) this).getHandler();
    }

    private static boolean apeiron$isReshuffle(BaseActionSource source) {
        return source instanceof ReshuffleActionSource;
    }

    private IAEItemStack apeiron$inject(IAEItemStack input, Actionable mode, BaseActionSource source) {
        try {
            return BigMEInventories.injectItemsBig(apeiron$handler(), input, mode, source);
        } catch (UnsupportedBigInventoryException ignored) {
            return input;
        }
    }

    private IAEItemStack apeiron$extract(IAEItemStack request, Actionable mode, BaseActionSource source) {
        try {
            return BigMEInventories.extractItemsBig(apeiron$handler(), request, mode, source);
        } catch (UnsupportedBigInventoryException ignored) {
            return null;
        }
    }

    private void apeiron$monitorDifference(IAEItemStack offered, IAEItemStack result, boolean extraction,
        BaseActionSource source) {
        BigInteger delta;
        if (extraction) {
            delta = result == null ? BigInteger.ZERO
                : BigAEItemStacks.stackSize(result)
                    .negate();
        } else {
            BigInteger leftover = result == null ? BigInteger.ZERO : BigAEItemStacks.stackSize(result);
            delta = BigAEItemStacks.stackSize(offered)
                .subtract(leftover);
        }
        if (delta.signum() == 0) return;

        IAEItemStack change = BigAEItemStacks.copyWithSize(offered, delta);
        postChangesToListeners(Collections.singletonList(change), source);
        ItemFlowGridCache flowCache = ((NetworkMonitor<?>) (Object) this).getGrid()
            .getCache(ItemFlowGridCache.class);
        flowCache.recordFlow(change, source);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void apeiron$monitorGenericDifference(IAEStack offered, IAEStack result, boolean extraction,
        BaseActionSource source) {
        BigInteger delta;
        if (extraction) {
            delta = result == null ? BigInteger.ZERO
                : BigAEStackValues.get(result)
                    .negate();
        } else {
            BigInteger leftover = result == null ? BigInteger.ZERO : BigAEStackValues.get(result);
            delta = BigAEStackValues.get(offered)
                .subtract(leftover);
        }
        if (delta.signum() == 0) return;
        IAEStack change = BigAEStackValues.set(offered.copy(), delta);
        postChangesToListeners(Collections.singletonList(change), source);
        ItemFlowGridCache flow = (ItemFlowGridCache) ((NetworkMonitor<?>) (Object) this).getGrid()
            .getCache(ItemFlowGridCache.class);
        flow.recordFlow(change, source);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public IAEStack injectItemsBig(IAEStack input, Actionable mode, BaseActionSource source) {
        if (input instanceof IAEItemStack) {
            return injectItemsBig((IAEItemStack) input, mode, source);
        }
        if (locked && !apeiron$isReshuffle(source)) return input;
        if (input == null) return null;
        for (Iterator<IStorageInterceptor> iterator = storageInterceptors.iterator(); iterator.hasNext();) {
            IStorageInterceptor interceptor = iterator.next();
            if (!interceptor.canAccept(input)) continue;
            if (interceptor instanceof BigStorageInterceptor) {
                input = (IAEStack) ((BigStorageInterceptor) interceptor).injectItemsBig(input, mode, source);
            } else {
                if (BigAEStackValues.isBig(input)) {
                    throw new UnsupportedOperationException(
                        "Storage interceptor has no exact-count implementation: " + interceptor.getClass()
                            .getName());
                }
                input = (IAEStack) interceptor.injectItems(input, mode, source);
            }
            if (mode == Actionable.MODULATE && interceptor.shouldRemoveInterceptor(input)) iterator.remove();
            if (input == null) return null;
        }

        NetworkMonitor<?> monitor = (NetworkMonitor<?>) (Object) this;
        IMEInventoryHandler handler = monitor.getHandler();
        if (mode == Actionable.SIMULATE) {
            return BigMEInventories.injectItemsBig(handler, input, mode, source);
        }
        IAEStack offered = input.copy();
        localDepthSemaphore++;
        IAEStack leftover;
        try {
            leftover = BigMEInventories.injectItemsBig(handler, input, mode, source);
        } finally {
            localDepthSemaphore--;
        }
        if (localDepthSemaphore == 0) apeiron$monitorGenericDifference(offered, leftover, false, source);
        return leftover;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public IAEStack extractItemsBig(IAEStack request, Actionable mode, BaseActionSource source) {
        if (request instanceof IAEItemStack) {
            return extractItemsBig((IAEItemStack) request, mode, source);
        }
        if (locked && !apeiron$isReshuffle(source)) return null;
        if (request == null) return null;
        NetworkMonitor<?> monitor = (NetworkMonitor<?>) (Object) this;
        IMEInventoryHandler handler = monitor.getHandler();
        if (mode == Actionable.SIMULATE) return BigMEInventories.extractItemsBig(handler, request, mode, source);
        localDepthSemaphore++;
        IAEStack extracted;
        try {
            extracted = BigMEInventories.extractItemsBig(handler, request, mode, source);
        } finally {
            localDepthSemaphore--;
        }
        if (localDepthSemaphore == 0) apeiron$monitorGenericDifference(request.copy(), extracted, true, source);
        return extracted;
    }

    @Override
    public IAEItemStack injectItemsBig(IAEItemStack input, Actionable mode, BaseActionSource source) {
        if (locked && !apeiron$isReshuffle(source)) return input;
        if (input == null) return null;

        for (Iterator<IStorageInterceptor> iterator = storageInterceptors.iterator(); iterator.hasNext();) {
            IStorageInterceptor interceptor = iterator.next();
            if (!interceptor.canAccept(input)) continue;
            if (BigAEItemStacks.isStackSizeBig(input)) {
                if (!(interceptor instanceof BigStorageInterceptor)) {
                    throw new UnsupportedOperationException(
                        "storage interceptor has no exact-count implementation: " + interceptor.getClass()
                            .getName());
                }
                input = ((BigStorageInterceptor) interceptor).injectItemsBig(input, mode, source);
            } else {
                input = (IAEItemStack) interceptor.injectItems(input, mode, source);
            }
            if (mode == Actionable.MODULATE && interceptor.shouldRemoveInterceptor(input)) iterator.remove();
            if (input == null) return null;
        }

        if (mode == Actionable.SIMULATE) return apeiron$inject(input, mode, source);

        IAEItemStack offered = input.copy();
        localDepthSemaphore++;
        IAEItemStack leftover;
        try {
            leftover = apeiron$inject(input, mode, source);
        } finally {
            localDepthSemaphore--;
        }
        if (localDepthSemaphore == 0) apeiron$monitorDifference(offered, leftover, false, source);
        return leftover;
    }

    @Override
    public IAEItemStack extractItemsBig(IAEItemStack request, Actionable mode, BaseActionSource source) {
        if (locked && !apeiron$isReshuffle(source)) return null;
        if (request == null) return null;
        if (mode == Actionable.SIMULATE) return apeiron$extract(request, mode, source);

        localDepthSemaphore++;
        IAEItemStack extracted;
        try {
            extracted = apeiron$extract(request, mode, source);
        } finally {
            localDepthSemaphore--;
        }
        if (localDepthSemaphore == 0) apeiron$monitorDifference(request.copy(), extracted, true, source);
        return extracted;
    }

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$legacyInject(IAEStack<?> input, Actionable mode, BaseActionSource source,
        CallbackInfoReturnable<IAEStack<?>> cir) {
        if (BigAEStackValues.isBig(input)) {
            cir.setReturnValue(injectItemsBig(input, mode, source));
        }
    }

    @Inject(method = "extractItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$legacyExtract(IAEStack<?> request, Actionable mode, BaseActionSource source,
        CallbackInfoReturnable<IAEStack<?>> cir) {
        if (BigAEStackValues.isBig(request)) {
            cir.setReturnValue(extractItemsBig(request, mode, source));
        }
    }
}
