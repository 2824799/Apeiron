package com.silvia.apeiron.config;

import java.io.File;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import net.minecraftforge.common.config.Configuration;

import com.silvia.apeiron.api.machine.parallel.ParallelMachine;

/** Independent immutable selections for each mod, published only after a complete configuration read. */
public final class UnlimitedParallelConfig<M extends Enum<M> & ParallelMachine> {

    public static final String CATEGORY_GENERAL = "general";
    public static final String CATEGORY_MACHINES = "machines";
    public static final String ENABLE_UNLIMITED_PARALLEL = "enableUnlimitedParallel";

    private final Class<M> machineType;
    private final String modName;
    private volatile Settings<M> settings;

    public UnlimitedParallelConfig(final Class<M> machineType, final String modName) {
        this.machineType = Objects.requireNonNull(machineType, "machineType");
        this.modName = Objects.requireNonNull(modName, "modName");
        settings = new Settings<>(false, EnumSet.noneOf(machineType));
    }

    public synchronized void load(final File configFile) {
        final Configuration configuration = new Configuration(configFile);
        try {
            final boolean enabled = configuration.getBoolean(
                ENABLE_UNLIMITED_PARALLEL,
                CATEGORY_GENERAL,
                false,
                modName + " 无限并行总开关。需要重启和完整控制器适配；本文件记录适配选择。" + "实际并行仍受材料、输出空间、能源和手动并行限额限制。");
            final EnumSet<M> selected = EnumSet.noneOf(machineType);
            for (final M machine : machineType.getEnumConstants()) {
                if (configuration.getBoolean(
                    machine.getKey(),
                    CATEGORY_MACHINES,
                    false,
                    machine.getDescription() + " 需要完整的输入、处理、输出适配；涉及耗电时使用直接无线能源接口。")) {
                    selected.add(machine);
                }
            }
            settings = new Settings<>(enabled, selected);
        } finally {
            if (configuration.hasChanged()) configuration.save();
        }
    }

    public boolean isEnabled() {
        return settings.enabled;
    }

    public boolean isSelected(final M machine) {
        return settings.selected.contains(Objects.requireNonNull(machine, "machine"));
    }

    /** A request does not install an adapter or assert that a native numerical maximum means unlimited. */
    public boolean isRequested(final M machine) {
        final Settings<M> current = settings;
        return current.enabled && current.selected.contains(Objects.requireNonNull(machine, "machine"));
    }

    private static final class Settings<M extends Enum<M>> {

        private final boolean enabled;
        private final Set<M> selected;

        private Settings(final boolean enabled, final EnumSet<M> selected) {
            this.enabled = enabled;
            this.selected = Collections.unmodifiableSet(EnumSet.copyOf(selected));
        }
    }
}
