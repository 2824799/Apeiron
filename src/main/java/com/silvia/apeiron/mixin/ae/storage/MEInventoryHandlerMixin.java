package com.silvia.apeiron.mixin.ae.storage;

import org.spongepowered.asm.mixin.Mixin;

import com.silvia.apeiron.ae.storage.BigIMEInventory;
import com.silvia.apeiron.ae.storage.BigMEInventories;
import com.silvia.apeiron.ae.storage.BigMEInventory;
import com.silvia.apeiron.compat.ReshuffleAccess;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.ReshuffleActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.me.storage.MEInventoryHandler;

/** Keep AE2's access, partition and reshuffle checks on the exact-count path. */
@Mixin(value = MEInventoryHandler.class, remap = false)
public abstract class MEInventoryHandlerMixin implements BigIMEInventory, BigMEInventory {

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public IAEStack injectItemsBig(IAEStack input, Actionable mode, BaseActionSource source) {
        if (input instanceof IAEItemStack) {
            return injectItemsBig((IAEItemStack) input, mode, source);
        }
        MEInventoryHandler handler = (MEInventoryHandler) (Object) this;
        if (!handler.canAccept(input) || source instanceof ReshuffleActionSource && !ReshuffleAccess.policy(handler)
            .hasPermission(AccessRestriction.WRITE)) {
            return input;
        }
        return BigMEInventories
            .injectItemsBig((appeng.api.storage.IMEInventory) handler.getInternal(), input, mode, source);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public IAEStack extractItemsBig(IAEStack request, Actionable mode, BaseActionSource source) {
        if (request instanceof IAEItemStack) {
            return extractItemsBig((IAEItemStack) request, mode, source);
        }
        MEInventoryHandler handler = (MEInventoryHandler) (Object) this;
        if (!handler.getAccess()
            .hasPermission(AccessRestriction.READ)
            || source instanceof ReshuffleActionSource && !ReshuffleAccess.policy(handler)
                .hasPermission(AccessRestriction.READ)) {
            return null;
        }
        if (handler.isExtractFilterActive() && !handler.getExtractPartitionList()
            .isEmpty()
            && !handler.getExtractFilterCondition()
                .test(request)) {
            return null;
        }
        return BigMEInventories
            .extractItemsBig((appeng.api.storage.IMEInventory) handler.getInternal(), request, mode, source);
    }

    @SuppressWarnings("unchecked")
    @Override
    public IAEItemStack injectItemsBig(IAEItemStack input, Actionable mode, BaseActionSource source) {
        MEInventoryHandler<IAEItemStack> handler = (MEInventoryHandler<IAEItemStack>) (Object) this;
        if (!handler.canAccept(input) || source instanceof ReshuffleActionSource && !ReshuffleAccess.policy(handler)
            .hasPermission(AccessRestriction.WRITE)) {
            return input;
        }
        return BigMEInventories.injectItemsBig(handler.getInternal(), input, mode, source);
    }

    @SuppressWarnings("unchecked")
    @Override
    public IAEItemStack extractItemsBig(IAEItemStack request, Actionable mode, BaseActionSource source) {
        MEInventoryHandler<IAEItemStack> handler = (MEInventoryHandler<IAEItemStack>) (Object) this;
        if (!handler.getAccess()
            .hasPermission(AccessRestriction.READ)
            || source instanceof ReshuffleActionSource && !ReshuffleAccess.policy(handler)
                .hasPermission(AccessRestriction.READ)) {
            return null;
        }
        if (handler.isExtractFilterActive() && !handler.getExtractPartitionList()
            .isEmpty()
            && !handler.getExtractFilterCondition()
                .test(request)) {
            return null;
        }
        return BigMEInventories.extractItemsBig(handler.getInternal(), request, mode, source);
    }
}
