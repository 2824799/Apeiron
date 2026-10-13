package com.silvia.apeiron.api.machine.parallel;

import java.math.BigInteger;
import java.util.UUID;

public interface BigPlasmaMixerController extends BigWirelessController {

    BigInteger getPlasmaMixerParallel();

    void setPlasmaMixerParallel(BigInteger value);

    int getPlasmaMixerDuration();

    void setPlasmaMixerDuration(int ticks);

    UUID getPlasmaMixerOwner();

    void recordPlasmaMixerEnergy(BigInteger energy);
}
