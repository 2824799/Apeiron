package com.silvia.apeiron.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** Runtime configuration shared by the early Mixin bootstrap and the mod lifecycle. */
public final class ApeironConfig {

    public static final String FILE_NAME = "apeiron.cfg";
    public static final String CATEGORY_MIXINS = "mixins";
    public static final String ENABLE_AE_MIXINS = "enableAeMixins";
    public static final String ENABLE_AE_SELF_RECURSIVE_CRAFTING = "enableAeSelfRecursiveCrafting";
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
    private static volatile boolean aeSelfRecursiveCraftingEnabled = true;
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
                "核心功能总开关：支持 AE2 大数存储、自动合成，以及 GT 机器的输入、输出和无线能源适配。\n" + "关闭后，上述功能及依赖它们的可选模组适配会一并停用；不会将已有大数存档转换为普通存档。\n"
                    + "默认开启。修改后必须重启游戏／服务端，客户端与服务端应保持一致。");
            configuration.getCategory(CATEGORY_MIXINS)
                .remove(ENABLE_AE_MIXINS);
            aeSelfRecursiveCraftingEnabled = configuration.getBoolean(
                ENABLE_AE_SELF_RECURSIVE_CRAFTING,
                CATEGORY_AE_MIXINS,
                true,
                "AE 自循环样板增强：允许同一张样板用产物补回同类输入，并按每轮净增量规划合成。默认开启；修改后重启，客户端与服务端保持一致。");
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
                "鸿蒙之眼大数输出：将完整数量的产物优先送入 Apeiron 的无限 ME 输出设备。\n"
                    + "关闭后停用此输出适配；增强模块的设置由 enableEyeOfHarmonyEnhancement 单独控制。\n"
                    + "需要 TecTech 和核心开关 enableAeMixins。默认开启；修改后重启，客户端与服务端保持一致。");
            tstOutputEnabled = configuration.getBoolean(
                ENABLE_TST_OUTPUT,
                CATEGORY_TST_MIXINS,
                true,
                "TST 大数适配：支持其公共输出流程，以及已适配的星核钻机、矿物处理厂等机器。\n" + "可向无限 ME 输出总线、输出仓和输出总成输送大批产物，也控制 TST 专用能源适配。\n"
                    + "关闭后停用这些 TST 专用适配；不会为尚未适配的机器自动增加功能。\n"
                    + "需要 TST 和核心开关 enableAeMixins。默认开启；修改后重启，客户端与服务端保持一致。");
            tstPatternGuardEnabled = configuration.getBoolean(
                ENABLE_TST_PATTERN_GUARD,
                CATEGORY_TST_MIXINS,
                true,
                "TST 样板编码保护：编码未得到有效样板时，跳过后续转换，避免报错。\n" + "正常编码与转换不受影响；关闭后恢复 TST 原有处理。需要 TST，不依赖核心开关。\n"
                    + "默认开启；修改后重启，客户端与服务端保持一致。");
            infinityCellEnabled = configuration.getBoolean(
                ENABLE_INFINITY_CELL,
                CATEGORY_INFINITY_CELL_MIXINS,
                true,
                "AE2 Infinity Cell 适配：使无限存储元件正确显示和存取大数物品、流体、源质等资源。\n" + "安装 AppEU 时也适配其资源；保留元件原有的存档身份与独立存储文件。\n"
                    + "关闭后停用 Apeiron 对这些元件的数量适配，不会删除元件内的内容。\n"
                    + "需要 AE2 Infinity Cell 和核心开关 enableAeMixins。默认开启；修改后重启，客户端与服务端保持一致。");
            lightweightWailaEnabled = configuration.getBoolean(
                ENABLE_LIGHTWEIGHT_WAILA,
                CATEGORY_WAILA,
                true,
                "精简 Waila／OmniOcular 悬浮提示数据，减少看向大量库存或样板时的卡顿与超大数据包。\n" + "保留运行状态；库存和配方只显示预览，完整内容请打开机器界面查看，不改变真实库存。\n"
                    + "影响所有方块实体的 Waila 数据大小，并精简 GT 的 OmniOcular 数据。\n"
                    + "自定义 OmniOcular 脚本若需要完整存档数据，可关闭此项；关闭后不再精简提示数据。\n"
                    + "默认开启；修改后重启，客户端与服务端保持一致。");
            MixinFeature.load(configuration);
            final String configuredStart = configuration
                .get(
                    CATEGORY_MACHINES,
                    "machineIdStart",
                    DEFAULT_MACHINE_ID_START,
                    "Apeiron 机器编号起点：从此编号起预留连续 100 个 GT 机器 ID。\n" + "默认 31300；可填 2049～32666。仅在与其他模组编号冲突时调整。\n"
                        + "已有存档应保留原值，否则已放置的机器和物品可能对应错误。\n"
                        + "修改后必须重启，客户端与服务端应使用相同编号。")
                .getString();
            machineIdStart = Integer.parseInt(configuredStart.trim());
            if (machineIdStart < MIN_MACHINE_ID_START || machineIdStart > MAX_MACHINE_ID_START) {
                throw new IllegalArgumentException("Apeiron machineIdStart must be in 2049..32666: " + machineIdStart);
            }
        } finally {
            // Forge does not mark updated comments as changes. Refresh help text while retaining loaded values.
            configuration.save();
        }
    }

    public static boolean areAeMixinsEnabled() {
        return aeMixinsEnabled;
    }

    public static boolean isAeSelfRecursiveCraftingEnabled() {
        return aeSelfRecursiveCraftingEnabled;
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
