package com.silvia.apeiron.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** Runtime configuration shared by the early Mixin bootstrap and the mod lifecycle. */
public final class ApeironConfig {

    public static final String FILE_NAME = "apeiron.cfg";
    public static final String CATEGORY_MIXINS = "mixins";
    public static final String ENABLE_AE_MIXINS = "enableAeMixins";

    private static volatile boolean aeMixinsEnabled = true;

    private ApeironConfig() {}

    /**
     * Loads the Forge configuration before Mixin starts applying classes.
     *
     * <p>The file is intentionally loaded once per classloader. Changing this option requires
     * a full game restart because Mixins are applied during class transformation.</p>
     */
    public static synchronized void load(final File configFile) {
        final Configuration configuration = new Configuration(configFile);
        try {
            aeMixinsEnabled = configuration.getBoolean(
                ENABLE_AE_MIXINS,
                CATEGORY_MIXINS,
                true,
                "Enable Apeiron's Applied Energistics 2 large-number Mixins. Requires a restart.");
        } finally {
            if (configuration.hasChanged()) {
                configuration.save();
            }
        }
    }

    public static boolean areAeMixinsEnabled() {
        return aeMixinsEnabled;
    }
}
