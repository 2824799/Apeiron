package com.silvia.apeiron.api.machine.parallel;

import java.math.BigInteger;

/** Called once after exact inputs and a wireless recipe have been committed. */
public interface BigWirelessRecipeListener {

    void beforeWirelessRecipeStart();

    void onWirelessRecipeStarted(BigInteger parallels, BigInteger totalEnergy);
}
