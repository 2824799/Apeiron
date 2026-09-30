package com.silvia.apeiron.ae.sync;

import appeng.container.sync.SyncRegistrar;

/** Access bridge for registering exact container synchronization channels. */
public interface BigSyncRegistrarAccess {

    SyncRegistrar apeiron$syncRegistrar();
}
