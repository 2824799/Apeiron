package com.silvia.apeiron.ae.crafting.core;

import java.math.BigInteger;

import appeng.api.storage.data.IAEStack;

/** Exact quantities exposed by a crafting CPU's final-output helper. */
public interface BigFinalOutput {

    BigInteger getOriginalCountBig();

    BigInteger getRemainingIngredientAmountBig(IAEStack<?> output);

    IAEStack<?> splitOutputToIngredientBig(IAEStack<?> output, appeng.api.config.Actionable mode);
}
