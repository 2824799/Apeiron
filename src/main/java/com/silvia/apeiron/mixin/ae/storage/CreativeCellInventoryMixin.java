package com.silvia.apeiron.mixin.ae.storage;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEItemStacks;
import com.silvia.apeiron.ae.storage.BigIMEInventory;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.CreativeCellInventory;

/** Preserve exact counts for configured item entries in creative cells. */
@Mixin(value = CreativeCellInventory.class, remap = false)
public abstract class CreativeCellInventoryMixin implements BigIMEInventory {

    @Shadow
    @Final
    private IItemList<?> listCache;

    @SuppressWarnings("unchecked")
    private IAEItemStack apeiron$find(IAEItemStack request) {
        return (IAEItemStack) ((IItemList<IAEItemStack>) (IItemList<?>) listCache).findPrecise(request);
    }

    @Override
    public IAEItemStack injectItemsBig(IAEItemStack input, Actionable mode, BaseActionSource source) {
        return apeiron$find(input) == null ? input : null;
    }

    @Override
    public IAEItemStack extractItemsBig(IAEItemStack request, Actionable mode, BaseActionSource source) {
        IAEItemStack stored = apeiron$find(request);
        if (stored == null) return null;

        BigInteger amount = BigAEItemStacks.stackSize(request)
            .min(BigAEItemStacks.stackSize(stored));
        return BigAEItemStacks.copyWithSize(stored, amount);
    }

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$legacyInject(IAEStack<?> input, Actionable mode, BaseActionSource source,
        CallbackInfoReturnable<IAEStack<?>> cir) {
        if (input instanceof IAEItemStack && BigAEItemStacks.isStackSizeBig((IAEItemStack) input)) {
            cir.setReturnValue(injectItemsBig((IAEItemStack) input, mode, source));
        }
    }

    @Inject(method = "extractItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$legacyExtract(IAEStack<?> request, Actionable mode, BaseActionSource source,
        CallbackInfoReturnable<IAEStack<?>> cir) {
        if (request instanceof IAEItemStack && BigAEItemStacks.isStackSizeBig((IAEItemStack) request)) {
            cir.setReturnValue(extractItemsBig((IAEItemStack) request, mode, source));
        }
    }
}
