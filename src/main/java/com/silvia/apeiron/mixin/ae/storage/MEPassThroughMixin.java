package com.silvia.apeiron.mixin.ae.storage;

import org.spongepowered.asm.mixin.Mixin;

import com.silvia.apeiron.ae.storage.BigIMEInventory;
import com.silvia.apeiron.ae.storage.BigMEInventories;
import com.silvia.apeiron.ae.storage.BigMEInventory;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.me.storage.MEPassThrough;

/** Forward exact operations through AE2's generic inventory adapter. */
@Mixin(value = MEPassThrough.class, remap = false)
public abstract class MEPassThroughMixin implements BigIMEInventory, BigMEInventory {

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public IAEStack injectItemsBig(IAEStack input, Actionable mode, BaseActionSource source) {
        if (input instanceof IAEItemStack) {
            return injectItemsBig((IAEItemStack) input, mode, source);
        }
        MEPassThrough wrapper = (MEPassThrough) (Object) this;
        return BigMEInventories
            .injectItemsBig((appeng.api.storage.IMEInventory) wrapper.getInternal(), input, mode, source);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public IAEStack extractItemsBig(IAEStack request, Actionable mode, BaseActionSource source) {
        if (request instanceof IAEItemStack) {
            return extractItemsBig((IAEItemStack) request, mode, source);
        }
        MEPassThrough wrapper = (MEPassThrough) (Object) this;
        return BigMEInventories
            .extractItemsBig((appeng.api.storage.IMEInventory) wrapper.getInternal(), request, mode, source);
    }

    @SuppressWarnings("unchecked")
    @Override
    public IAEItemStack injectItemsBig(IAEItemStack input, Actionable mode, BaseActionSource source) {
        MEPassThrough<IAEItemStack> wrapper = (MEPassThrough<IAEItemStack>) (Object) this;
        return BigMEInventories.injectItemsBig(wrapper.getInternal(), input, mode, source);
    }

    @SuppressWarnings("unchecked")
    @Override
    public IAEItemStack extractItemsBig(IAEItemStack request, Actionable mode, BaseActionSource source) {
        MEPassThrough<IAEItemStack> wrapper = (MEPassThrough<IAEItemStack>) (Object) this;
        return BigMEInventories.extractItemsBig(wrapper.getInternal(), request, mode, source);
    }
}
