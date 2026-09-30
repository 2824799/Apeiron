package com.silvia.apeiron.ae.automation;

import java.math.BigInteger;

/** Exact threshold access exposed by the multi-slot level-emitter container. */
public interface BigAdvancedLevelEmitterContainerAccess {

    BigInteger getLevelBig(int slot);

    void setLevelBig(int slot, BigInteger value);
}
