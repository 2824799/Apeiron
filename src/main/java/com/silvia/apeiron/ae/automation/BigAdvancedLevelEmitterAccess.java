package com.silvia.apeiron.ae.automation;

import java.math.BigInteger;

/** Exact threshold access for the multi-slot AE level emitter. */
public interface BigAdvancedLevelEmitterAccess {

    BigInteger getReportingValueBig(int slot);

    void setReportingValueBig(int slot, BigInteger value);
}
