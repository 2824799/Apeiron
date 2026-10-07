package com.silvia.apeiron.api.machine.parallel;

/** A validated recipe commits inputs only after the controller accepts its startup. */
public interface PreparedWirelessRecipe {

    void commit(int duration);
}
