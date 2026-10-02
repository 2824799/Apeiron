package com.silvia.apeiron.mixin.ae.storage;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.stack.InfiniteAEStack;
import com.silvia.apeiron.ae.storage.BigIMEInventory;
import com.silvia.apeiron.ae.storage.BigMEInventory;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.CreativeCellInventory;

/** AE2 and AE2FC both use this generic inventory for creative item and fluid cells. */
@Mixin(value = CreativeCellInventory.class, remap = false)
public abstract class CreativeCellInventoryMixin implements BigIMEInventory, BigMEInventory {

    @Shadow
    @Final
    private IItemList<?> listCache;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void apeiron$renewable(CallbackInfo ci) {
        for (Object entry : listCache) ((InfiniteAEStack) entry).setInfinite(true);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private boolean apeiron$contains(IAEStack<?> request) {
        return request != null && ((IItemList) listCache).findPrecise(request) != null;
    }

    @Override
    public IAEStack<?> injectItemsBig(IAEStack<?> input, Actionable mode, BaseActionSource source) {
        return apeiron$contains(input) ? null : input;
    }

    @Override
    public IAEStack<?> extractItemsBig(IAEStack<?> request, Actionable mode, BaseActionSource source) {
        if (!apeiron$contains(request) || BigAEStackValues.get(request)
            .signum() <= 0) return null;
        return request.copy();
    }

    @Override
    public IAEItemStack injectItemsBig(IAEItemStack input, Actionable mode, BaseActionSource source) {
        return (IAEItemStack) injectItemsBig((IAEStack<?>) input, mode, source);
    }

    @Override
    public IAEItemStack extractItemsBig(IAEItemStack request, Actionable mode, BaseActionSource source) {
        return (IAEItemStack) extractItemsBig((IAEStack<?>) request, mode, source);
    }

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$inject(IAEStack<?> input, Actionable mode, BaseActionSource source,
        CallbackInfoReturnable<IAEStack<?>> cir) {
        cir.setReturnValue(injectItemsBig(input, mode, source));
    }

    @Inject(method = "extractItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$extract(IAEStack<?> request, Actionable mode, BaseActionSource source,
        CallbackInfoReturnable<IAEStack<?>> cir) {
        cir.setReturnValue(extractItemsBig(request, mode, source));
    }
}
