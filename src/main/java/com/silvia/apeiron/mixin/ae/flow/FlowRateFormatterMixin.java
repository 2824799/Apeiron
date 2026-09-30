package com.silvia.apeiron.mixin.ae.flow;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.flow.BigFlowFormatter;
import com.silvia.apeiron.ae.flow.BigFlowRate;

import appeng.me.cache.ItemFlowGridCache.FlowRate;
import appeng.util.FlowRateFormatter;

/** Renders exact flow-rate tooltips when a rate exceeds long. */
@Mixin(value = FlowRateFormatter.class, remap = false)
public abstract class FlowRateFormatterMixin {

    @Inject(method = "formatTooltipLine", at = @At("HEAD"), cancellable = true)
    private static void apeiron$formatExact(final FlowRate rate, final CallbackInfoReturnable<String> cir) {
        if (rate instanceof BigFlowRate && ((BigFlowRate) rate).isBigFlow()) {
            cir.setReturnValue(BigFlowFormatter.format(rate));
        }
    }
}
