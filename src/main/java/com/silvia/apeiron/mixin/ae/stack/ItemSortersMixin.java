package com.silvia.apeiron.mixin.ae.stack;

import java.util.Comparator;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.api.storage.data.IAEStack;
import appeng.util.ItemSorters;

/** Sorts the AE terminal by exact stored quantities. */
@Mixin(value = ItemSorters.class, remap = false)
public abstract class ItemSortersMixin {

    @Shadow
    @Final
    @Mutable
    public static Comparator<IAEStack<?>> CONFIG_BASED_SORT_BY_SIZE;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void apeiron$exactSizeComparator(final CallbackInfo ci) {
        CONFIG_BASED_SORT_BY_SIZE = (left, right) -> BigAEStackValues.get(right).compareTo(BigAEStackValues.get(left));
    }
}
