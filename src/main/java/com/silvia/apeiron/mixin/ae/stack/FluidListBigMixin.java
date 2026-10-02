package com.silvia.apeiron.mixin.ae.stack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.stack.BigAERequestableStack;
import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.api.storage.data.IAEFluidStack;
import appeng.util.item.FluidList;

/** Keeps multi-cell fluid aggregation exact, including merges into existing list entries. */
@Mixin(value = FluidList.class, remap = false)
public abstract class FluidListBigMixin {

    @Shadow
    public abstract IAEFluidStack findPrecise(IAEFluidStack request);

    @Shadow
    private void putFluidRecord(final IAEFluidStack stack) {
        throw new AssertionError("Mixin shadow");
    }

    @Inject(
        method = "addStorage(Lappeng/api/storage/data/IAEFluidStack;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$addStorage(final IAEFluidStack option, final CallbackInfo ci) {
        if (!BigAEStackValues.isBig(option) && !BigAEStackValues.isInfinite(option)) return;
        final IAEFluidStack stored = findPrecise(option);
        if (stored != null) {
            BigAEStackValues.addStorage(stored, option);
            ci.cancel();
        }
    }

    @Inject(
        method = "addRequestable(Lappeng/api/storage/data/IAEFluidStack;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$addRequestable(final IAEFluidStack option, final CallbackInfo ci) {
        if (option == null) return;
        final IAEFluidStack stored = findPrecise(option);
        if (stored != null) {
            ((BigAERequestableStack) stored).incCountRequestableBig(BigAEStackValues.getCountRequestable(option));
            ci.cancel();
        } else if (!BigAEStackValues.fitsLong(BigAEStackValues.getCountRequestable(option))) {
            final IAEFluidStack copy = option.copy();
            copy.setStackSize(0);
            copy.setCraftable(false);
            putFluidRecord(copy);
            ci.cancel();
        }
    }
}
