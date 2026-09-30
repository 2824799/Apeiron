package com.silvia.apeiron.ae.automation;

import appeng.api.networking.security.BaseActionSource;

/** Access to the protected automation-bus target and source from shared exact-count helpers. */
public interface BigPartTargetAccess {

    Object apeiron$getTarget();

    BaseActionSource apeiron$getSource();
}
