package com.silvia.apeiron.mixin.ae.storage;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEItemStack;
import com.silvia.apeiron.ae.stack.BigAEItemStacks;
import com.silvia.apeiron.ae.storage.BigIMEInventory;

import appeng.api.config.Actionable;
import appeng.api.implementations.items.IBiometricCard;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.SecurityInventory;
import appeng.tile.misc.TileSecurity;

/** Keep AE's security-card inventory exact-count aware. */
@Mixin(value = SecurityInventory.class, remap = false)
public abstract class SecurityInventoryMixin implements BigIMEInventory {

    @Shadow
    @Final
    private IItemList<IAEItemStack> storedItems;

    @Shadow
    @Final
    private TileSecurity securityTile;

    @Shadow
    private boolean hasPermission(BaseActionSource source) {
        throw new AssertionError("mixin shadow was not replaced");
    }

    @Shadow
    public abstract boolean canAccept(IAEItemStack input);

    @Override
    public IAEItemStack injectItemsBig(IAEItemStack input, Actionable mode, BaseActionSource source) {
        if (input == null || !hasPermission(source)
            || !(input.getItem() instanceof IBiometricCard)
            || !canAccept(input)) return input;
        if (mode == Actionable.SIMULATE) return null;

        storedItems.add(input);
        securityTile.inventoryChanged();
        return null;
    }

    @Override
    public IAEItemStack extractItemsBig(IAEItemStack request, Actionable mode, BaseActionSource source) {
        if (request == null || !hasPermission(source)) return null;

        IAEItemStack target = storedItems.findPrecise(request);
        if (target == null || BigAEItemStacks.stackSize(target)
            .signum() <= 0) return null;

        BigInteger amount = BigAEItemStacks.stackSize(request)
            .min(BigAEItemStacks.stackSize(target));
        IAEItemStack result = BigAEItemStacks.copyWithSize(target, amount);
        if (mode == Actionable.MODULATE) {
            ((BigAEItemStack) target).decStackSizeBig(amount);
            securityTile.inventoryChanged();
        }
        return result;
    }

    // Select the typed implementation explicitly: javac also generates an IAEStack bridge with this name.
    @Inject(
        method = "injectItems(Lappeng/api/storage/data/IAEItemStack;Lappeng/api/config/Actionable;Lappeng/api/networking/security/BaseActionSource;)Lappeng/api/storage/data/IAEItemStack;",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$legacyInject(IAEItemStack input, Actionable mode, BaseActionSource source,
        CallbackInfoReturnable<IAEItemStack> cir) {
        if (BigAEItemStacks.isStackSizeBig(input)) {
            cir.setReturnValue(injectItemsBig(input, mode, source));
        }
    }

    @Inject(
        method = "extractItems(Lappeng/api/storage/data/IAEItemStack;Lappeng/api/config/Actionable;Lappeng/api/networking/security/BaseActionSource;)Lappeng/api/storage/data/IAEItemStack;",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$legacyExtract(IAEItemStack request, Actionable mode, BaseActionSource source,
        CallbackInfoReturnable<IAEItemStack> cir) {
        if (BigAEItemStacks.isStackSizeBig(request)) {
            cir.setReturnValue(extractItemsBig(request, mode, source));
        }
    }

    @Inject(
        method = "getAvailableItem(Lappeng/api/storage/data/IAEItemStack;I)Lappeng/api/storage/data/IAEItemStack;",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$legacyAvailable(IAEItemStack request, int iteration,
        CallbackInfoReturnable<IAEItemStack> cir) {
        IAEItemStack target = storedItems.findPrecise(request);
        if (BigAEItemStacks.isStackSizeBig(target)) cir.setReturnValue(target.copy());
    }
}
