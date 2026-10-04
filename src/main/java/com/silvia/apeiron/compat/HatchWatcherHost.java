package com.silvia.apeiron.compat;

/** Version-independent notification endpoint for Apeiron inputs. */
public interface HatchWatcherHost {

    void addWatcherCompat(Object watcher);

    void removeWatcherCompat(Object watcher);
}
