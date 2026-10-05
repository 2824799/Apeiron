package com.silvia.apeiron.api.machine.parallel;

import java.math.BigInteger;
import java.util.List;

import appeng.api.storage.data.IAEStack;
import gregtech.api.util.GTRecipe;

/** Explicit opt-in for specialized output rules around the common exact recipe transaction. */
public interface BigRecipeOutputProvider {

    boolean hasExactRecipeOutputs();

    List<IAEStack<?>> calculateRecipeOutputsBig(GTRecipe recipe, BigInteger parallels);
}
