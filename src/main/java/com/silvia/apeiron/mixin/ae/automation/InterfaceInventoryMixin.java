package com.silvia.apeiron.mixin.ae.automation;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.silvia.apeiron.ae.storage.BigIMEInventory;
import com.silvia.apeiron.ae.storage.BigInventoryAdaptors;

import appeng.api.config.Actionable;
import appeng.api.config.InsertionMode;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.DualityInterface;
import appeng.me.storage.MEMonitorIInventory;
import appeng.util.inv.AdaptorIInventory;

/** Retains the interface's self-request exclusion on the new exact input/output methods. */
@Mixin(targets = "appeng.helpers.DualityInterface$InterfaceInventory", remap = false)
public abstract class InterfaceInventoryMixin implements BigIMEInventory {

    @Shadow @Final private DualityInterface this$0;

    private boolean apeiron$isInterfaceRequest(final BaseActionSource source) {
        return source != null && source.getClass().getName().equals("appeng.helpers.DualityInterface$InterfaceRequestSource");
    }

    @Override
    public IAEItemStack injectItemsBig(final IAEItemStack input, final Actionable mode, final BaseActionSource source) {
        if (this.apeiron$isInterfaceRequest(source)) return input;
        final IAEItemStack result = (IAEItemStack) BigInventoryAdaptors.addStackBig(
            new AdaptorIInventory(this.this$0.getStorage()), input, InsertionMode.DEFAULT, mode == Actionable.SIMULATE);
        if (mode == Actionable.MODULATE) ((MEMonitorIInventory) (Object) this).onTick();
        return result;
    }

    @Override
    public IAEItemStack extractItemsBig(final IAEItemStack request, final Actionable mode, final BaseActionSource source) {
        if (this.apeiron$isInterfaceRequest(source)) return null;
        final IAEItemStack result = BigInventoryAdaptors.extractStackBig(new AdaptorIInventory(this.this$0.getStorage()),
            request, mode == Actionable.SIMULATE);
        if (mode == Actionable.MODULATE && result != null) ((MEMonitorIInventory) (Object) this).onTick();
        return result;
    }
}
