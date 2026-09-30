package com.silvia.apeiron.ae.crafting.core;

import java.math.BigInteger;

/** Exact amount decoded from an AE crafting request packet. */
public interface BigCraftRequest {

    BigInteger getCraftAmountBig();
}
