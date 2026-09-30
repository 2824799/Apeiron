package com.silvia.apeiron.mixin.ae.stack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.stack.BigAEItemStack;
import com.silvia.apeiron.ae.stack.BigAEItemStacks;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.HashBasedItemList;
import appeng.util.item.ItemList;

/** Preserve exact counts when AE2 combines stacks inside its item lists. */
@Mixin(value = { ItemList.class, HashBasedItemList.class }, remap = false)
public abstract class AEItemListsMixin {

    @Shadow(remap = false)
    public abstract IAEItemStack findPrecise(IAEItemStack stack);

    @Shadow(remap = false)
    private void putItemRecord(IAEItemStack stack) {
        throw new AssertionError("mixin shadow was not replaced");
    }

    @Inject(method = "addStorage(Lappeng/api/storage/data/IAEItemStack;)V", at = @At("HEAD"), cancellable = true)
    private void apeiron$addStorageExact(IAEItemStack option, CallbackInfo ci) {
        if (option == null || !(option instanceof BigAEItemStack) || !((BigAEItemStack) option).isStackSizeBig()) {
            return;
        }
        IAEItemStack stored = findPrecise(option);
        if (stored != null) {
            BigAEItemStacks.addStorage(stored, option);
            ci.cancel();
        }
    }

    @Inject(method = "addRequestable(Lappeng/api/storage/data/IAEItemStack;)V", at = @At("HEAD"), cancellable = true)
    private void apeiron$addRequestableExact(IAEItemStack option, CallbackInfo ci) {
        if (option == null) return;
        boolean includeCrafts = (Object) this instanceof ItemList;
        IAEItemStack stored = findPrecise(option);
        if (stored != null) {
            BigAEItemStacks.addRequestable(stored, option, includeCrafts);
            ci.cancel();
        } else if (option instanceof BigAEItemStack) {
            BigAEItemStack exact = (BigAEItemStack) option;
            if (exact.isCountRequestableBig() || includeCrafts && exact.isCountRequestableCraftsBig()) {
                IAEItemStack copy = option.copy();
                copy.setStackSize(0L);
                copy.setCraftable(false);
                putItemRecord(copy);
                ci.cancel();
            }
        }
    }
}
