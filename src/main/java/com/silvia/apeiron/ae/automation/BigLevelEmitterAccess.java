package com.silvia.apeiron.ae.automation;

import java.math.BigInteger;

/** Exact threshold access for the ordinary AE level emitter. */
public interface BigLevelEmitterAccess {

    BigInteger getReportingValueBig();

    void setReportingValueBig(BigInteger value);
}
