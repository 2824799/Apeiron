package com.silvia.apeiron.mixin.ae.flow;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.math.BigNumberFormatter;

import appeng.api.storage.data.IAEStack;
import appeng.parts.reporting.AbstractPartMonitor;

/** Preserves exact counts in AE monitor panels and their update text. */
@Mixin(value = AbstractPartMonitor.class, remap = false)
public abstract class AbstractPartMonitorMixin {

    @Shadow
    private IAEStack<?> configuredItem;

    @Shadow
    private String lastHumanReadableText;

    @Inject(method = "updateStackSize", at = @At("HEAD"), cancellable = true)
    private void apeiron$updateExact(final IAEStack<?> newStack, final CallbackInfo ci) {
        if (newStack == null || !BigAEStackValues.isBig(newStack)) return;
        BigAEStackValues.set(configuredItem, BigAEStackValues.get(newStack));
        final String text = BigNumberFormatter.formatCompact(BigAEStackValues.get(newStack));
        if (!text.equals(lastHumanReadableText)) {
            lastHumanReadableText = text;
            ((AbstractPartMonitor) (Object) this).getHost()
                .markForUpdate();
        }
        ci.cancel();
    }
}
