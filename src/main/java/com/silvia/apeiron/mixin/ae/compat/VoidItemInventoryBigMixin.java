package com.silvia.apeiron.mixin.ae.compat;

import java.math.BigDecimal;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.compat.BigCondenserAccess;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.storage.VoidItemInventory;

/** Sends oversized item voiding power through the exact condenser bridge. */
@Mixin(value = VoidItemInventory.class, remap = false)
public abstract class VoidItemInventoryBigMixin {

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$injectExact(final IAEItemStack input, final Actionable mode, final BaseActionSource source,
        final CallbackInfoReturnable<IAEItemStack> cir) {
        if (mode == Actionable.MODULATE && input != null && !BigAEStackValues.fitsLong(BigAEStackValues.get(input))) {
            final VoidItemInventory self = (VoidItemInventory) (Object) this;
            try {
                final java.lang.reflect.Field field = VoidItemInventory.class.getDeclaredField("target");
                field.setAccessible(true);
                final Object target = field.get(self);
                if (target instanceof BigCondenserAccess exact) {
                    exact.apeiron$addPowerBig(new BigDecimal(BigAEStackValues.get(input)));
                    cir.setReturnValue(null);
                }
            } catch (ReflectiveOperationException ignored) {}
        }
    }
}
