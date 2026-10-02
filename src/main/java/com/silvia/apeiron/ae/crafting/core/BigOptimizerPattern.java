package com.silvia.apeiron.ae.crafting.core;

import java.math.BigInteger;

import appeng.api.networking.crafting.ICraftingPatternDetails;

public interface BigOptimizerPattern {

    void addCraftingTaskBig(ICraftingPatternDetails pattern, BigInteger crafts);

    BigInteger getCraftsBig();
}
