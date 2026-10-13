package com.silvia.apeiron.api.machine.parallel;

import java.math.BigInteger;
import java.util.List;

import appeng.api.storage.data.IAEStack;
import gregtech.api.util.GTRecipe;

/** Processing logic or controller opt-in for specialized outputs around the common exact recipe transaction. */
public interface BigRecipeOutputProvider {

    boolean hasExactRecipeOutputs();

    List<IAEStack<?>> calculateRecipeOutputsBig(GTRecipe recipe, BigInteger parallels);
}
