package com.silvia.apeiron.verification;

import com.silvia.apeiron.compat.DependencyCapabilities;

/** Optional cache fixture; never included in production artifacts. */
public final class FlowStatisticsFixture {

    private FlowStatisticsFixture() {}

    public static Object create() {
        return DependencyCapabilities.hasClass("appeng.me.cache.ItemFlowGridCache") ? Modern.create() : null;
    }

    private static final class Modern {

        static Object create() {
            return new appeng.me.cache.ItemFlowGridCache(null);
        }
    }
}
