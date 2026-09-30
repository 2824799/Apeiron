package com.silvia.apeiron.ae.automation;

import java.math.BigInteger;

/** Exact threshold access exposed by the ordinary level-emitter container. */
public interface BigLevelEmitterContainerAccess {

    BigInteger getLevelBig();

    void setLevelBig(BigInteger value);
}
