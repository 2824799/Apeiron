package com.silvia.apeiron.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** Runtime configuration shared by the early Mixin bootstrap and the mod lifecycle. */
public final class ApeironConfig {

    public static final String FILE_NAME = "apeiron.cfg";
    public static final String CATEGORY_MIXINS = "mixins";
    public static final String ENABLE_AE_MIXINS = "enableAeMixins";
    public static final String CATEGORY_AE_MIXINS = "mixins.appliedenergistics2";
    public static final String CATEGORY_TECTECH_MIXINS = "mixins.tectech";
    public static final String ENABLE_EYE_OUTPUT = "enableEyeOfHarmonyBigOutput";
    public static final String CATEGORY_TST_MIXINS = "mixins.twistspacetechnology";
    public static final String ENABLE_TST_OUTPUT = "enableTstBigOutput";
    public static final String ENABLE_TST_PATTERN_GUARD = "enableTstPatternEncodeGuard";
    public static final String CATEGORY_INFINITY_CELL_MIXINS = "mixins.aeinfinitycell";
    public static final String ENABLE_INFINITY_CELL = "enableInfinityCellBigStorage";
    public static final String CATEGORY_MACHINES = "machines";
    public static final String CATEGORY_WAILA = "mixins.waila";
    public static final String ENABLE_LIGHTWEIGHT_WAILA = "enableLightweightSnapshots";
    public static final int MACHINE_ID_COUNT = 100;
    public static final int DEFAULT_MACHINE_ID_START = 31300;
    public static final int MIN_MACHINE_ID_START = 2049;
    public static final int MAX_MACHINE_ID_START = 32666;

    private static volatile boolean aeMixinsEnabled = true;
    private static volatile boolean eyeOutputEnabled = true;
    private static volatile boolean tstOutputEnabled = true;
    private static volatile boolean tstPatternGuardEnabled = true;
    private static volatile boolean infinityCellEnabled = true;
    private static volatile boolean lightweightWailaEnabled = true;
    private static volatile int machineIdStart = DEFAULT_MACHINE_ID_START;

    private ApeironConfig() {}

    /** Uses the same paths during early Mixin bootstrap and the normal mod lifecycle. */
    public static synchronized void loadFromDirectory(final File forgeConfigDirectory) {
        final File directory = ApeironConfigFiles.prepareDirectory(forgeConfigDirectory);
        load(new File(directory, FILE_NAME));
        TstUnlimitedParallelConfig.load(new File(directory, TstUnlimitedParallelConfig.FILE_NAME));
        GtnlUnlimitedParallelConfig.load(new File(directory, GtnlUnlimitedParallelConfig.FILE_NAME));
    }

    /**
     * Loads the Forge configuration before Mixin starts applying classes.
     *
     * <p>
     * Changing these options requires a full game restart because Mixins and machine IDs are applied at startup.
     * </p>
     */
    public static synchronized void load(final File configFile) {
        final Configuration configuration = new Configuration(configFile);
        try {
            final boolean legacyAeEnabled = configuration.hasKey(CATEGORY_MIXINS, ENABLE_AE_MIXINS)
                ? configuration.get(CATEGORY_MIXINS, ENABLE_AE_MIXINS, true)
                    .getBoolean(true)
                : true;
            if (!configuration.hasKey(CATEGORY_AE_MIXINS, ENABLE_AE_MIXINS)) {
                configuration.get(CATEGORY_AE_MIXINS, ENABLE_AE_MIXINS, true)
                    .set(legacyAeEnabled);
            }
            aeMixinsEnabled = configuration.getBoolean(
                ENABLE_AE_MIXINS,
                CATEGORY_AE_MIXINS,
                true,
                "Enable the cooperating AE large-number and GT input/output/energy core. Disable as one unit; requires a restart and matching client/server settings.");
            configuration.getCategory(CATEGORY_MIXINS)
                .remove(ENABLE_AE_MIXINS);
            if (configuration.hasKey("mixins.gregtech", ENABLE_EYE_OUTPUT)) {
                final boolean oldValue = configuration.get("mixins.gregtech", ENABLE_EYE_OUTPUT, true)
                    .getBoolean(true);
                if (!configuration.hasKey(CATEGORY_TECTECH_MIXINS, ENABLE_EYE_OUTPUT)) {
                    configuration.get(CATEGORY_TECTECH_MIXINS, ENABLE_EYE_OUTPUT, true)
                        .set(oldValue);
                }
                configuration.getCategory("mixins.gregtech")
                    .remove(ENABLE_EYE_OUTPUT);
                if (configuration.getCategory("mixins.gregtech")
                    .isEmpty()
                    && configuration.getCategory("mixins.gregtech")
                        .getChildren()
                        .isEmpty()) {
                    configuration.removeCategory(configuration.getCategory("mixins.gregtech"));
                }
            }
            eyeOutputEnabled = configuration.getBoolean(
                ENABLE_EYE_OUTPUT,
                CATEGORY_TECTECH_MIXINS,
                true,
                "Enable Eye of Harmony big-number output. TecTech is bundled with GregTech but has the distinct mod ID tectech. "
                    + "Requires AE large-number Mixins and a restart. Prioritizes Apeiron ME output blocks.");
            tstOutputEnabled = configuration.getBoolean(
                ENABLE_TST_OUTPUT,
                CATEGORY_TST_MIXINS,
                true,
                "Enable Twist Space Technology shared big-number ME output. Requires AE Mixins and restart. "
                    + "Supports Infinite ME Output Bus, Hatch and Assembly; custom machine output logic needs separate adapters.");
            tstPatternGuardEnabled = configuration.getBoolean(
                ENABLE_TST_PATTERN_GUARD,
                CATEGORY_TST_MIXINS,
                true,
                "Skip TST's conversion callback when an AE pattern encoding produces no output. Preserves successful conversions. Requires restart.");
            infinityCellEnabled = configuration.getBoolean(
                ENABLE_INFINITY_CELL,
                CATEGORY_INFINITY_CELL_MIXINS,
                true,
                "Enable exact AE2 Infinity Cell transfers and inventory lists, including essentia and optional AppEU stacks. "
                    + "Requires AE Mixins and a restart. Preserves Infinity Cell's existing UUID and external save format.");
            lightweightWailaEnabled = configuration.getBoolean(
                ENABLE_LIGHTWEIGHT_WAILA,
                CATEGORY_WAILA,
                true,
                "Use operating snapshots instead of full GregTech disk saves for OmniOcular, and bound Waila packet "
                    + "size for all tile entities. Inventories and recipes are previews; full contents remain in their GUIs. "
                    + "Disable for custom OmniOcular scripts that require complete save data. Requires a restart.");
            MixinFeature.load(configuration);
            final String configuredStart = configuration.get(
                CATEGORY_MACHINES,
                "machineIdStart",
                DEFAULT_MACHINE_ID_START,
                "First GregTech machine ID reserved for Apeiron. Reserves 100 consecutive IDs. "
                    + "Valid starting IDs: 2049..32666. Requires a restart; preserve this value for existing saves.")
                .getString();
            machineIdStart = Integer.parseInt(configuredStart.trim());
            if (machineIdStart < MIN_MACHINE_ID_START || machineIdStart > MAX_MACHINE_ID_START) {
                throw new IllegalArgumentException("Apeiron machineIdStart must be in 2049..32666: " + machineIdStart);
            }
        } finally {
            if (configuration.hasChanged()) {
                configuration.save();
            }
        }
    }

    public static boolean areAeMixinsEnabled() {
        return aeMixinsEnabled;
    }

    public static boolean isLightweightWailaEnabled() {
        return lightweightWailaEnabled;
    }

    public static boolean isEyeOfHarmonyBigOutputEnabled() {
        return eyeOutputEnabled;
    }

    public static boolean isTstBigOutputEnabled() {
        return tstOutputEnabled;
    }

    public static boolean isTstPatternEncodeGuardEnabled() {
        return tstPatternGuardEnabled;
    }

    public static boolean isInfinityCellBigStorageEnabled() {
        return infinityCellEnabled;
    }

    public static int getMachineIdStart() {
        return machineIdStart;
    }

    public static int getMachineIdEnd() {
        return machineIdStart + MACHINE_ID_COUNT - 1;
    }

    public static int getMachineId(final int offset) {
        if (offset < 0 || offset >= MACHINE_ID_COUNT)
            throw new IllegalArgumentException("Machine ID offset: " + offset);
        return machineIdStart + offset;
    }
}
