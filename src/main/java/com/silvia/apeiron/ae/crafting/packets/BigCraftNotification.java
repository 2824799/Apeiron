package com.silvia.apeiron.ae.crafting.packets;

import java.math.BigInteger;

/** Exact completion count attached to AE's legacy crafting notification object. */
public interface BigCraftNotification {

    BigInteger getOutputsCountBig();
}
