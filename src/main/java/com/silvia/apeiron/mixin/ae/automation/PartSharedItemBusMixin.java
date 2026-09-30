package com.silvia.apeiron.mixin.ae.automation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.silvia.apeiron.ae.automation.BigPartTargetAccess;

import appeng.api.networking.security.BaseActionSource;
import appeng.parts.automation.PartSharedItemBus;

/** Exposes shared protected bus state to exact-count automation mixins. */
@Mixin(value = PartSharedItemBus.class, remap = false)
public abstract class PartSharedItemBusMixin implements BigPartTargetAccess {

    @Shadow
    protected abstract Object getTarget();

    @Shadow
    protected BaseActionSource mySrc;

    @Override
    public Object apeiron$getTarget() {
        return getTarget();
    }

    @Override
    public BaseActionSource apeiron$getSource() {
        return mySrc;
    }
}
