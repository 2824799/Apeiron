package com.silvia.apeiron.api.machine.parallel;

import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;

/** The common controller owns exact configuration, running recipe and pending outputs. */
public interface BigWirelessController extends BigParallelController {

    WirelessRecipeState getWirelessRecipeState();
}
