package com.silvia.apeiron.mixin.ae.flow;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.math.BigNumberFormatter;

import appeng.parts.reporting.PartThroughputMonitor;
import appeng.util.IWideReadableNumberConverter;
import appeng.api.storage.data.IAEStack;

/** Renders exact large amounts on throughput monitors. */
@Mixin(value = PartThroughputMonitor.class, remap = false)
public abstract class PartThroughputMonitorMixin {

    @Unique
    private static final ThreadLocal<BigInteger> APEIRON_RENDERING_COUNT = new ThreadLocal<>();

    @Inject(method = "tesrRenderItemNumber", at = @At("HEAD"))
    private void apeiron$beginExactRender(final IAEStack<?> stack, final CallbackInfo ci) {
        if (BigAEStackValues.isBig(stack)) APEIRON_RENDERING_COUNT.set(BigAEStackValues.get(stack));
    }

    @Inject(method = "tesrRenderItemNumber", at = @At("RETURN"))
    private void apeiron$endExactRender(final IAEStack<?> stack, final CallbackInfo ci) {
        APEIRON_RENDERING_COUNT.remove();
    }

    @Redirect(
        method = "tesrRenderItemNumber",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/util/IWideReadableNumberConverter;toWideReadableForm(J)Ljava/lang/String;"))
    private String apeiron$formatExact(final IWideReadableNumberConverter converter, final long value) {
        final BigInteger exact = APEIRON_RENDERING_COUNT.get();
        return exact == null ? converter.toWideReadableForm(value) : BigNumberFormatter.formatCompact(exact);
    }
}
