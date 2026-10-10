package com.silvia.apeiron.api.machine.lanthanides;

/** A beam-crafter recipe whose particle requirements were paid from the same input inventory. */
public interface BeamItemInputController {

    void setItemParticleRecipe(boolean enabled);

    void prepareItemParticleRecipe(int particleA, int particleB);
}
