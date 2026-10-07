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
                modName + " 原生无限并行扩展的预留选项，默认关闭。\n"
                    + "当前版本仅保存所选机器，不会直接改变游戏中的并行；开启也不会自动解除限制。\n"
                    + "Apeiron 能源仓已支持的并行设置请在机器电源面板调整，不受本文件控制。");
            final EnumSet<M> selected = EnumSet.noneOf(machineType);
            for (final M machine : machineType.getEnumConstants()) {
                if (configuration.getBoolean(
                    machine.getKey(),
                    CATEGORY_MACHINES,
                    false,
                    machine.getDescription() + "\n预留的单机选择，默认关闭；需与本文件总开关一起开启。当前版本仅保存选择，不改变机器运行。")) {
                    selected.add(machine);
                }
            }
            settings = new Settings<>(enabled, selected);
        } finally {
            // Also update descriptions in existing files; Forge does not track comment changes.
            configuration.save();
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
