package com.silvia.apeiron.config;

import java.io.File;

import com.silvia.apeiron.common.integration.tst.parallel.TstParallelMachine;

/** Records requested TST adapters independently of the existing ME output switches. */
public final class TstUnlimitedParallelConfig {

    public static final String FILE_NAME = "tst-unlimited-parallel.cfg";
    public static final String CATEGORY_GENERAL = UnlimitedParallelConfig.CATEGORY_GENERAL;
    public static final String CATEGORY_MACHINES = UnlimitedParallelConfig.CATEGORY_MACHINES;
    public static final String ENABLE_UNLIMITED_PARALLEL = UnlimitedParallelConfig.ENABLE_UNLIMITED_PARALLEL;

    private static final UnlimitedParallelConfig<TstParallelMachine> CONFIG = new UnlimitedParallelConfig<>(
        TstParallelMachine.class,
        "Twist Space Technology（TwistSpaceTechnology）");

    private TstUnlimitedParallelConfig() {}

    public static synchronized void load(final File configFile) {
        CONFIG.load(configFile);
    }

    public static boolean isEnabled() {
        return CONFIG.isEnabled();
    }

    public static boolean isSelected(final TstParallelMachine machine) {
        return CONFIG.isSelected(machine);
    }

    /** A request is not an active adapter; callers must also validate their input, output and energy capabilities. */
    public static boolean isRequested(final TstParallelMachine machine, final int nativeMaxParallel) {
        return CONFIG.isRequested(machine) && nativeMaxParallel == Integer.MAX_VALUE;
    }
}
