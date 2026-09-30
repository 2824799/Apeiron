package com.silvia.apeiron.mixin.ae.automation;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigIMEInventory;
import com.silvia.apeiron.ae.storage.BigInventoryAdaptors;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.parts.automation.PartFormationPlane;

/** The plane places one physical stack; the unplaced AE remainder always keeps its exact count. */
@Mixin(value = PartFormationPlane.class, remap = false)
public abstract class PartFormationPlaneMixin implements BigIMEInventory {

    @Override
    public IAEItemStack injectItemsBig(final IAEItemStack input, final Actionable mode, final BaseActionSource source) {
        if (input == null || BigAEStackValues.get(input).signum() <= 0) return input;
        final BigInteger offered = BigAEStackValues.get(input);
        final BigInteger chunk = offered.min(BigInteger.valueOf(input.getItemStack().getMaxStackSize()));
        final IAEItemStack physical = BigAEStackValues.copyWithSize(input, chunk);
        final IAEItemStack rejected = ((PartFormationPlane) (Object) this).injectItems(physical, mode, source);
        final BigInteger left = BigAEStackValues.get(rejected);
        BigInventoryAdaptors.checkReturnedAmount(left, chunk);
        final BigInteger remaining = offered.subtract(chunk.subtract(left));
        return remaining.signum() == 0 ? null : BigAEStackValues.copyWithSize(input, remaining);
    }

    @Override
    public IAEItemStack extractItemsBig(final IAEItemStack request, final Actionable mode,
        final BaseActionSource source) {
        return null;
    }

    @Inject(method = "injectItems(Lappeng/api/storage/data/IAEItemStack;Lappeng/api/config/Actionable;Lappeng/api/networking/security/BaseActionSource;)Lappeng/api/storage/data/IAEItemStack;",
        at = @At("HEAD"), cancellable = true)
    private void apeiron$placeBig(final IAEItemStack input, final Actionable mode, final BaseActionSource source,
        final CallbackInfoReturnable<IAEItemStack> cir) {
        if (BigAEStackValues.isBig(input)) cir.setReturnValue(this.injectItemsBig(input, mode, source));
    }
}
