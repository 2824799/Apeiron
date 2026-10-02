package com.silvia.apeiron.mixin.aeinfinitycell.stack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.stack.BigAEStackValues;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import thaumicenergistics.common.storage.AEEssentiaStack;

/** Existing-entry list merges otherwise read only the saturated long size. */
@Pseudo
@Mixin(targets = "thaumicenergistics.common.storage.EssentiaList", remap = false)
public abstract class EssentiaListBigMixin {

    @Shadow
    @Final
    private ObjectOpenHashSet<AEEssentiaStack> records;

    @Shadow
    public abstract AEEssentiaStack findPrecise(AEEssentiaStack request);

    @Inject(
        method = "addStorage(Lthaumicenergistics/common/storage/AEEssentiaStack;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$addStorage(final AEEssentiaStack option, final CallbackInfo ci) {
        if (!BigAEStackValues.isBig(option)) return;
        final AEEssentiaStack stored = findPrecise(option);
        if (stored != null) {
            BigAEStackValues.addStorage(stored, option);
            ci.cancel();
        }
    }

    @Inject(
        method = "addRequestable(Lthaumicenergistics/common/storage/AEEssentiaStack;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$addRequestable(final AEEssentiaStack option, final CallbackInfo ci) {
        if (option == null) return;
        final AEEssentiaStack stored = findPrecise(option);
        if (stored != null) {
            ((com.silvia.apeiron.ae.stack.BigAERequestableStack) stored)
                .incCountRequestableBig(BigAEStackValues.getCountRequestable(option));
            ci.cancel();
        } else if (!BigAEStackValues.fitsLong(BigAEStackValues.getCountRequestable(option))) {
            final AEEssentiaStack copy = option.copy();
            copy.setStackSize(0);
            copy.setCraftable(false);
            records.add(copy);
            ci.cancel();
        }
    }
}
