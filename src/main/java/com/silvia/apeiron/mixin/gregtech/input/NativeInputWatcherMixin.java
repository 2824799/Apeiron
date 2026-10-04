package com.silvia.apeiron.mixin.gregtech.input;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.compat.HatchWatcherHost;

/** Adapts native watcher registration while leaving ordinary hatches untouched. */
@Mixin(
    targets = { "gregtech.api.metatileentity.implementations.MTEHatch",
        "gregtech.common.tileentities.machines.MTEHatchInputBusME",
        "gregtech.common.tileentities.machines.MTEHatchInputME" },
    remap = false)
public abstract class NativeInputWatcherMixin {

    @Inject(method = "addWatcher", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$addWatcher(@Coerce Object watcher, CallbackInfo ci) {
        if ((Object) this instanceof HatchWatcherHost) {
            ((HatchWatcherHost) this).addWatcherCompat(watcher);
            ci.cancel();
        }
    }

    @Inject(method = "removeWatcher", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$removeWatcher(@Coerce Object watcher, CallbackInfo ci) {
        if ((Object) this instanceof HatchWatcherHost) {
            ((HatchWatcherHost) this).removeWatcherCompat(watcher);
            ci.cancel();
        }
    }
}
