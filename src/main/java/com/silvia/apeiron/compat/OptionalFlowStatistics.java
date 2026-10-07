package com.silvia.apeiron.compat;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;

/** Storage works on releases preceding the optional native flow statistics cache. */
public final class OptionalFlowStatistics {

    private static final boolean AVAILABLE = DependencyCapabilities.hasClass("appeng.me.cache.ItemFlowGridCache");

    private OptionalFlowStatistics() {}

    public static void record(IGrid grid, IAEStack<?> change, BaseActionSource source) {
        if (AVAILABLE) ModernRecorder.record(grid, change, source);
    }

    private static final class ModernRecorder {

        static void record(IGrid grid, IAEStack<?> change, BaseActionSource source) {
            if (grid == null) return;
            appeng.me.cache.ItemFlowGridCache cache = grid.getCache(appeng.me.cache.ItemFlowGridCache.class);
            if (cache != null) cache.recordFlow(change, source);
        }
    }
}
