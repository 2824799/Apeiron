package com.silvia.apeiron.ae.compat;

import java.math.BigDecimal;

/** Exact power input bridge for AE's condenser void inventories. */
public interface BigCondenserAccess {

    void apeiron$addPowerBig(BigDecimal rawPower);
}
