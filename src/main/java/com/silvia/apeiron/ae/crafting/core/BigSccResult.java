package com.silvia.apeiron.ae.crafting.core;

import java.util.Set;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import it.unimi.dsi.fastutil.longs.LongObjectPair;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongMap;
import it.unimi.dsi.fastutil.objects.AbstractObject2ObjectMap;

/** Public accessors for the package-private SCC result used by AE's fast calculator. */
public interface BigSccResult {

    AbstractObject2ObjectMap<IAEStack<?>, LongObjectPair<ICraftingPatternDetails>> getPatternsBig();

    AbstractObject2LongMap<IAEStack<?>> getInDegreeBig();

    Set<IAEStack<?>> getLoopingPatternsBig();
}
