package com.silvia.apeiron.config;

import java.io.File;
import java.math.BigInteger;

import com.silvia.apeiron.common.integration.gtnl.parallel.GtnlParallelMachine;

/** GTNL's requested adapters are selected independently of TST, using the original native maximum. */
public final class GtnlUnlimitedParallelConfig {

    public static final String FILE_NAME = "gtnl-unlimited-parallel.cfg";
    public static final String CATEGORY_GENERAL = UnlimitedParallelConfig.CATEGORY_GENERAL;
    public static final String CATEGORY_MACHINES = UnlimitedParallelConfig.CATEGORY_MACHINES;
    public static final String ENABLE_UNLIMITED_PARALLEL = UnlimitedParallelConfig.ENABLE_UNLIMITED_PARALLEL;

    private static final UnlimitedParallelConfig<GtnlParallelMachine> CONFIG = new UnlimitedParallelConfig<>(
        GtnlParallelMachine.class,
        "GT Not Leisure（sciencenotleisure）");

    private GtnlUnlimitedParallelConfig() {}

    public static synchronized void load(final File configFile) {
        CONFIG.load(configFile);
    }

    public static boolean isEnabled() {
        return CONFIG.isEnabled();
    }

    public static boolean isSelected(final GtnlParallelMachine machine) {
        return CONFIG.isSelected(machine);
    }

    /** Selection only. A complete controller adapter must validate inputs, outputs, energy and machine modes. */
    public static boolean isRequested(final GtnlParallelMachine machine, final BigInteger nativeMaximum) {
        return machine.isNativeUnlimitedMode(nativeMaximum) && CONFIG.isRequested(machine);
    }

    public static boolean isRequested(final GtnlParallelMachine machine, final int nativeMaximum) {
        return isRequested(machine, BigInteger.valueOf(nativeMaximum));
    }
}
