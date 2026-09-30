package com.silvia.apeiron.mixin.ae.crafting.core;

import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.silvia.apeiron.ae.crafting.core.BigSccResult;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.crafting.fast.SccResolver;
import it.unimi.dsi.fastutil.longs.LongObjectPair;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongMap;
import it.unimi.dsi.fastutil.objects.AbstractObject2ObjectMap;

/** Exposes the fast resolver's package-private result to the exact calculator. */
@Mixin(value = SccResolver.Result.class, remap = false)
public abstract class SccResolverResultMixin implements BigSccResult {

    @Shadow
    @Final
    AbstractObject2ObjectMap<IAEStack<?>, LongObjectPair<ICraftingPatternDetails>> patterns;

    @Shadow
    @Final
    AbstractObject2LongMap<IAEStack<?>> inDegree;

    @Shadow
    @Final
    Set<IAEStack<?>> loopingPatterns;

    @Override
    public AbstractObject2ObjectMap<IAEStack<?>, LongObjectPair<ICraftingPatternDetails>> getPatternsBig() {
        return this.patterns;
    }

    @Override
    public AbstractObject2LongMap<IAEStack<?>> getInDegreeBig() {
        return this.inDegree;
    }

    @Override
    public Set<IAEStack<?>> getLoopingPatternsBig() {
        return this.loopingPatterns;
    }
}
