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
import appeng.api.storage.data.IAEFluidStack;
import appeng.me.storage.VoidFluidInventory;

/** Sends oversized fluid voiding power through the exact condenser bridge. */
@Mixin(value = VoidFluidInventory.class, remap = false)
public abstract class VoidFluidInventoryBigMixin {

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$injectExact(final IAEFluidStack input, final Actionable mode, final BaseActionSource source,
        final CallbackInfoReturnable<IAEFluidStack> cir) {
        if (mode == Actionable.MODULATE && input != null && !BigAEStackValues.fitsLong(BigAEStackValues.get(input))) {
            final VoidFluidInventory self = (VoidFluidInventory) (Object) this;
            try {
                final java.lang.reflect.Field field = VoidFluidInventory.class.getDeclaredField("target");
                field.setAccessible(true);
                final Object target = field.get(self);
                if (target instanceof BigCondenserAccess exact) {
                    exact.apeiron$addPowerBig(new BigDecimal(BigAEStackValues.get(input))
                            .divide(BigDecimal.valueOf(1000), 12, java.math.RoundingMode.HALF_UP));
                    cir.setReturnValue(null);
                }
            } catch (ReflectiveOperationException ignored) {}
        }
    }
}
