package com.silvia.apeiron.mixin.ae.flow;

import java.util.Map;
import java.util.WeakHashMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.flow.BigFlowRate;
import com.silvia.apeiron.ae.flow.BigFlowStore;

import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.core.AEConfig;
import appeng.me.cache.ItemFlowGridCache;
import appeng.me.cache.ItemFlowGridCache.FlowRate;

/** Stores exact network-flow deltas while keeping AE2's public map compatible. */
@Mixin(value = ItemFlowGridCache.class, remap = false)
public abstract class ItemFlowGridCacheMixin {

    private static final Map<ItemFlowGridCache, BigFlowStore> APEIRON_STORES = new WeakHashMap<>();

    @Shadow
    private boolean trackingEnabled;

    @Inject(method = "recordFlow", at = @At("HEAD"), cancellable = true)
    private void apeiron$recordExact(final IAEStack<?> diff, final BaseActionSource source, final CallbackInfo ci) {
        if (!AEConfig.instance.enableItemFlowTracking || !this.trackingEnabled || !BigAEStackValues.isBig(diff)) return;
        synchronized (APEIRON_STORES) {
            APEIRON_STORES.computeIfAbsent((ItemFlowGridCache) (Object) this, ignored -> new BigFlowStore())
                    .record(diff, source);
        }
        ci.cancel();
    }

    @Inject(method = "getAllRecentFlow", at = @At("RETURN"))
    private void apeiron$mergeExact(final CallbackInfoReturnable<Map<IAEStack<?>, FlowRate>> cir) {
        final Map<IAEStack<?>, BigFlowStore.Totals> totals;
        synchronized (APEIRON_STORES) {
            final BigFlowStore store = APEIRON_STORES.get((ItemFlowGridCache) (Object) this);
            if (store == null) return;
            totals = store.totals();
        }

        final Map<IAEStack<?>, FlowRate> result = cir.getReturnValue();
        for (final Map.Entry<IAEStack<?>, BigFlowStore.Totals> entry : totals.entrySet()) {
            FlowRate rate = result.get(entry.getKey());
            if (rate == null) {
                rate = new FlowRate(
                        BigAEStackValues.saturatedLong(entry.getValue().in()),
                        BigAEStackValues.saturatedLong(entry.getValue().out()));
                result.put(entry.getKey(), rate);
            }
            if (rate instanceof BigFlowRate) {
                ((BigFlowRate) rate).setBigFlow(entry.getValue().in(), entry.getValue().out());
            }
        }
    }

    @Inject(method = "onUpdateTick", at = @At("TAIL"))
    private void apeiron$purgeExact(final CallbackInfo ci) {
        synchronized (APEIRON_STORES) {
            final BigFlowStore store = APEIRON_STORES.get((ItemFlowGridCache) (Object) this);
            if (store != null) store.purge(AEConfig.instance.itemFlowTrackingWindowMinutes);
        }
    }

    @Inject(method = "setTrackingEnabled", at = @At("HEAD"))
    private void apeiron$clearExact(final boolean enabled, final CallbackInfo ci) {
        if (enabled) return;
        synchronized (APEIRON_STORES) {
            final BigFlowStore store = APEIRON_STORES.get((ItemFlowGridCache) (Object) this);
            if (store != null) store.clear();
        }
    }
}
