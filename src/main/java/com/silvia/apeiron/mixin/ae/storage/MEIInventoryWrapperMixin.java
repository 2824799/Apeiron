package com.silvia.apeiron.mixin.ae.storage;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEItemStacks;
import com.silvia.apeiron.ae.storage.BigIMEInventory;
import com.silvia.apeiron.ae.storage.BigInventoryAdaptors;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.me.storage.MEIInventoryWrapper;
import appeng.util.InventoryAdaptor;
import net.minecraft.inventory.IInventory;
import net.minecraftforge.common.util.ForgeDirection;

/** Exact item operations for AE's direct IInventory wrapper. */
@Mixin(value = MEIInventoryWrapper.class, remap = false)
public abstract class MEIInventoryWrapperMixin implements BigIMEInventory {

    @Shadow
    @Final
    private IInventory target;

    @Shadow
    @Final
    private InventoryAdaptor adaptor;

    @Inject(method = "injectItems(Lappeng/api/storage/data/IAEItemStack;Lappeng/api/config/Actionable;Lappeng/api/networking/security/BaseActionSource;)Lappeng/api/storage/data/IAEItemStack;", at = @At("HEAD"), cancellable = true)
    private void apeiron$legacyInject(final IAEItemStack input, final Actionable mode,
        final BaseActionSource source, final CallbackInfoReturnable<IAEItemStack> cir) {
        if (BigAEItemStacks.isStackSizeBig(input) || input.getStackSize() >= Integer.MAX_VALUE) {
            cir.setReturnValue(injectItemsBig(input, mode, source));
        }
    }

    @Inject(method = "extractItems(Lappeng/api/storage/data/IAEItemStack;Lappeng/api/config/Actionable;Lappeng/api/networking/security/BaseActionSource;)Lappeng/api/storage/data/IAEItemStack;", at = @At("HEAD"), cancellable = true)
    private void apeiron$legacyExtract(final IAEItemStack request, final Actionable mode,
        final BaseActionSource source, final CallbackInfoReturnable<IAEItemStack> cir) {
        if (BigAEItemStacks.isStackSizeBig(request) || request.getStackSize() >= Integer.MAX_VALUE) {
            cir.setReturnValue(extractItemsBig(request, mode, source));
        }
    }

    @Override
    public IAEItemStack injectItemsBig(final IAEItemStack input, final Actionable mode,
        final BaseActionSource source) {
        InventoryAdaptor selected = adaptor != null ? adaptor
            : InventoryAdaptor.getAdaptor(target, ForgeDirection.UNKNOWN);
        return (IAEItemStack) BigInventoryAdaptors.addStackBig(
            selected,
            input,
            appeng.api.config.InsertionMode.DEFAULT,
            mode == Actionable.SIMULATE);
    }

    @Override
    public IAEItemStack extractItemsBig(final IAEItemStack request, final Actionable mode,
        final BaseActionSource source) {
        InventoryAdaptor selected = adaptor != null ? adaptor
            : InventoryAdaptor.getAdaptor(target, ForgeDirection.UNKNOWN);
        return BigInventoryAdaptors.extractStackBig(selected, request, mode == Actionable.SIMULATE);
    }
}
