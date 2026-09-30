package com.silvia.apeiron.mixin.ae.stack;

import java.util.ArrayList;
import java.util.Collection;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import com.silvia.apeiron.ae.stack.BigAEItemStacks;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.ItemImmutableList;

/** Keeps immutable unions of AE item lists exact. */
@Mixin(value = ItemImmutableList.class, remap = false)
public abstract class ItemImmutableListMixin {

    @Shadow
    @Final
    private IItemList<IAEItemStack>[] lists;

    @Overwrite
    public IAEItemStack findPrecise(final IAEItemStack request) {
        IAEItemStack result = null;
        for (final IItemList<IAEItemStack> list : this.lists) {
            final IAEItemStack found = list.findPrecise(request);
            if (found == null) continue;
            if (result == null) result = found.copy();
            else BigAEItemStacks.addStorage(result, found);
        }
        return result;
    }

    @Overwrite
    public Collection<IAEItemStack> findFuzzy(final IAEItemStack request, final FuzzyMode fuzzy) {
        final Collection<IAEItemStack> result = new ArrayList<>();
        for (final IItemList<IAEItemStack> list : this.lists) {
            for (final IAEItemStack found : list.findFuzzy(request, fuzzy)) {
                boolean merged = false;
                for (final IAEItemStack existing : result) {
                    if (existing.isSameType(found)) {
                        BigAEItemStacks.addStorage(existing, found);
                        merged = true;
                        break;
                    }
                }
                if (!merged) result.add(found.copy());
            }
        }
        return result;
    }
}
