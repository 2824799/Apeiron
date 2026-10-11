package com.silvia.apeiron.api.machine.parallel;

import java.math.BigInteger;

import gregtech.api.util.GTRecipe;

/** Machines which amplify a whole recipe must also amplify its exact energy budget. */
public interface BigRecipeEnergyProvider {

    BigInteger getRecipeEnergyMultiplier(GTRecipe recipe);
}
