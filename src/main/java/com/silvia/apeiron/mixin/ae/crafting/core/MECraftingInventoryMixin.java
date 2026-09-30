package com.silvia.apeiron.mixin.ae.crafting.core;

import java.math.BigInteger;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.crafting.core.BigMECraftingInventory;

import appeng.api.config.Actionable;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;

/** Prevents the crafting model from truncating an exact item stack at long. */
@Mixin(value = MECraftingInventory.class, remap = false)
public abstract class MECraftingInventoryMixin implements BigMECraftingInventory {

    @Shadow
    @Final
    private Map<IAEStackType<?>, IItemList<IAEStack>> inventoryMap;

    @Shadow
    private boolean logExtracted;

    @Shadow
    private IItemList<IAEStack<?>> extractedCache;

    @Shadow
    private boolean logInjections;

    @Shadow
    private IItemList<IAEStack<?>> injectedCache;

    @Override
    public IAEStack<?> extractItemsBig(final IAEStack<?> request, final Actionable mode) {
        if (request == null) return null;
        final IItemList<IAEStack> list = this.inventoryMap.get(request.getStackType());
        if (list == null) return null;
        final IAEStack<?> stored = list.findPrecise(request);
        if (stored == null) return null;

        final BigInteger available = BigAEStackValues.get(stored);
        if (available.signum() <= 0) return null;
        final BigInteger requested = BigAEStackValues.get(request);
        if (requested.signum() <= 0) return null;

        if (available.compareTo(requested) >= 0) {
            if (mode == Actionable.MODULATE) {
                BigAEStackValues.set(stored, available.subtract(requested));
                if (this.logExtracted) this.extractedCache.add(request);
            }
            return request;
        }

        final IAEStack<?> result = request.copy();
        BigAEStackValues.set(result, available);
        if (mode == Actionable.MODULATE) {
            BigAEStackValues.set(stored, BigInteger.ZERO);
            if (this.logExtracted) this.extractedCache.add(result);
        }
        return result;
    }

    @Override
    public void injectItemsBig(final IAEStack<?> input, final Actionable mode) {
        if (input == null || mode != Actionable.MODULATE) return;
        this.inventoryMap.get(input.getStackType()).add(input);
        if (this.logInjections) this.injectedCache.add(input);
    }

    @Inject(method = "extractItems", at = @At("HEAD"), cancellable = true)
    private <StackType extends IAEStack<StackType>> void apeiron$extractExact(final StackType request,
        final Actionable mode, final CallbackInfoReturnable<StackType> cir) {
        if (BigAEStackValues.isBig(request)) {
            @SuppressWarnings("unchecked")
            final StackType result = (StackType) extractItemsBig(request, mode);
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "injectItems(Lappeng/api/storage/data/IAEStack;Lappeng/api/config/Actionable;)V", at = @At("HEAD"), cancellable = true)
    private void apeiron$injectExact(final IAEStack<?> input, final Actionable mode, final CallbackInfo ci) {
        if (BigAEStackValues.isBig(input)) {
            injectItemsBig(input, mode);
            ci.cancel();
        }
    }
}
