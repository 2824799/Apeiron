package com.silvia.apeiron.api.machine.parallel;

import gregtech.api.util.GTRecipe;

/** Explicit opt-in when specialized input callbacks can be represented by one exact input recipe. */
public interface BigRecipeInputProvider {

    boolean hasExactRecipeInputs();

    GTRecipe getExactInputRecipe(GTRecipe recipe);
}
