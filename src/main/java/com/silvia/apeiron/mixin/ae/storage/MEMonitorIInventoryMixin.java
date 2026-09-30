package com.silvia.apeiron.mixin.ae.storage;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEItemStacks;
import com.silvia.apeiron.ae.storage.BigIMEInventory;
import com.silvia.apeiron.ae.storage.BigInventoryAdaptors;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.me.storage.MEMonitorIInventory;
import appeng.util.InventoryAdaptor;

/** Exact item input/output for AE's monitor wrapper around ordinary inventories. */
@Mixin(value = MEMonitorIInventory.class, remap = false)
public abstract class MEMonitorIInventoryMixin implements BigIMEInventory {

    @Shadow
    @Final
    private InventoryAdaptor adaptor;

    @Inject(method = "injectItems(Lappeng/api/storage/data/IAEItemStack;Lappeng/api/config/Actionable;Lappeng/api/networking/security/BaseActionSource;)Lappeng/api/storage/data/IAEItemStack;", at = @At("HEAD"), cancellable = true)
    private void apeiron$legacyInject(final IAEItemStack input, final Actionable mode, final BaseActionSource source,
        final CallbackInfoReturnable<IAEItemStack> cir) {
        if (input instanceof IAEItemStack && (BigAEItemStacks.isStackSizeBig((IAEItemStack) input)
            || input.getStackSize() >= Integer.MAX_VALUE)) {
            cir.setReturnValue(injectItemsBig((IAEItemStack) input, mode, source));
        }
    }

    @Inject(method = "extractItems(Lappeng/api/storage/data/IAEItemStack;Lappeng/api/config/Actionable;Lappeng/api/networking/security/BaseActionSource;)Lappeng/api/storage/data/IAEItemStack;", at = @At("HEAD"), cancellable = true)
    private void apeiron$legacyExtract(final IAEItemStack request, final Actionable mode,
        final BaseActionSource source, final CallbackInfoReturnable<IAEItemStack> cir) {
        if (request instanceof IAEItemStack && (BigAEItemStacks.isStackSizeBig((IAEItemStack) request)
            || request.getStackSize() >= Integer.MAX_VALUE)) {
            cir.setReturnValue(extractItemsBig((IAEItemStack) request, mode, source));
        }
    }

    @Override
    public IAEItemStack injectItemsBig(final IAEItemStack input, final Actionable mode,
        final BaseActionSource source) {
        if (input == null || input.getStackSize() == 0) return null;
        IAEStack<?> remainder = BigInventoryAdaptors.addStackBig(
            adaptor,
            input,
            appeng.api.config.InsertionMode.DEFAULT,
            mode == Actionable.SIMULATE);
        if (mode == Actionable.MODULATE) {
            ((MEMonitorIInventory) (Object) this).onTick();
        }
        return (IAEItemStack) remainder;
    }

    @Override
    public IAEItemStack extractItemsBig(final IAEItemStack request, final Actionable mode,
        final BaseActionSource source) {
        if (request == null || request.getStackSize() == 0) return null;
        IAEItemStack result = BigInventoryAdaptors.extractStackBig(
            adaptor,
            request,
            mode == Actionable.SIMULATE);
        if (mode == Actionable.MODULATE && result != null) ((MEMonitorIInventory) (Object) this).onTick();
        return result;
    }
}
