package com.silvia.apeiron.mixin.proghatches;

import java.math.BigInteger;

import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;

import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import codechicken.nei.ItemStackMap;
import codechicken.nei.ItemStackSet;
import reobf.proghatches.gt.metatileentity.util.ISpecialOptimize;

/** Optional bridge preserves block-local multipliers instead of editing the player's encoded patterns. */
@Mixin(value = MTEInfinitePatternInputAssembly.class, remap = false)
public abstract class PatternInputOptimizationMixin implements ISpecialOptimize {

    @Override
    public void optimize(ItemStackMap<Pair<Object, Integer>> map) {
        MTEInfinitePatternInputAssembly machine = (MTEInfinitePatternInputAssembly) (Object) this;
        if (!machine.allowsPatternOptimization()) return;
        for (int slot = 0; slot < MTEInfinitePatternInputAssembly.PATTERN_COUNT; slot++) {
            ICraftingPatternDetails details = machine.getPatternDetails(slot);
            if (details == null) continue;
            Pair<Object, Integer> entry = map.get(details.getPattern());
            if (entry == null || entry.getRight() == 0) continue;
            int bits = entry.getRight();
            BigInteger current = machine.getMultiplierBig(slot);
            machine.setMultiplierBig(
                slot,
                (bits > 0 ? current.shiftLeft(bits) : current.shiftRight(-bits)).max(BigInteger.ONE));
        }
    }

    @Override
    public void blacklist(ItemStackSet blacklist) {
        MTEInfinitePatternInputAssembly machine = (MTEInfinitePatternInputAssembly) (Object) this;
        for (int slot = 0; slot < MTEInfinitePatternInputAssembly.PATTERN_COUNT; slot++) {
            ICraftingPatternDetails details = machine.getPatternDetails(slot);
            if (details != null) blacklist.add(details.getPattern());
        }
    }
}
