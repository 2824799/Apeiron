package com.silvia.apeiron.config;

import net.minecraftforge.common.config.Configuration;

/** Switch whole cooperating integrations, not individual injectors which depend on each other's interfaces. */
public enum MixinFeature {

    CORE(null, null),
    GTNL("mixins.sciencenotleisure", "enableWirelessIntegration"),
    BEAMLINE("mixins.gregtech", "enableBeamlineInputs"),
    QUANTUM("mixins.gregtech", "enableQuantumEnhancement"),
    SPACE_ELEVATOR("mixins.gregtech", "enableSpaceElevatorIntegration"),
    EYE_ENHANCEMENT("mixins.tectech", "enableEyeOfHarmonyEnhancement"),
    PROGRAMMABLE_HATCHES("mixins.programmablehatches", "enablePatternOptimization"),
    PACKET_DIAGNOSTICS("mixins.network", "enablePayloadDiagnostics"),
    EYE_OUTPUT(null, null),
    TST_OUTPUT(null, null),
    TST_PATTERN(null, null),
    INFINITY_CELL(null, null),
    WAILA_DISPLAY(null, null),
    WAILA_BUDGET(null, null);

    public final String category;
    public final String key;
    private volatile boolean enabled = true;

    MixinFeature(String category, String key) {
        this.category = category;
        this.key = key;
    }

    static void load(Configuration config) {
        for (MixinFeature feature : values()) if (feature.key != null) feature.enabled = config.getBoolean(
            feature.key,
            feature.category,
            true,
            feature.playerDescription() + "\n默认开启；关闭后停用这一整组适配。修改后必须重启，客户端与服务端应保持一致。"
                + (feature == PACKET_DIAGNOSTICS ? "" : "\n需要对应模组，并依赖核心开关 enableAeMixins。"));
    }

    private String playerDescription() {
        switch (this) {
            case GTNL:
                return "GT Not Leisure 无线机器适配：保留原生跨配方执行，支持大数产物与耗电。\n" + "适用其无线机器系列（如超生高速）。装终极无限能源仓后，无需无线升级即可无线供电，"
                    + "并按 Apeiron 面板设置并行和时间；未装 Apeiron 能源仓时保留原生并行及超频规则。";
            case BEAMLINE:
                return "束流机器输入适配：束流源室、靶室可读取无限样板输入总成中的独立配方材料。\n"
                    + "关闭后不提供此输入适配。移除粒子检测的选项由 EyeOfHarmonyBuffer 模组管理，本项不会移除检测。";
            case QUANTUM:
                return "量子操纵者增强方块适配：允许结构识别增强方块。\n" + "安装增强方块后，产物概率固定为 100%，可用样板中的催化剂编程器电路代替大宗催化剂仓，"
                    + "并启用 Apeiron 能源仓的并行、电压或时间设置。未安装增强方块时保留机器原有机制。";
            case SPACE_ELEVATOR:
                return "太空电梯适配：子模块可使用主模块的 Apeiron 无限能源仓及其无线账户。\n" + "普通仓保留子模块原有并行上限；终极仓为装配、采矿、抽水模块提供大数并行与输出支持。";
            case EYE_ENHANCEMENT:
                return "鸿蒙之眼增强模块适配：允许结构识别增强模块，安装后可调整时间与成功率、解除星阵上限，" + "并使用无限存储输入设备供料。\n"
                    + "默认时间 128 Tick、成功率 0.5；具体数值在模块界面设置。大数输出另由 enableEyeOfHarmonyBigOutput 控制。";
            case PROGRAMMABLE_HATCHES:
                return "Programmable Hatches 样板优化适配：允许优化矩阵与 Apeiron 样板倍增配合，减少重复配送。\n" + "也处理样板优化过程中的超大数量；关闭后停用这些优化适配。";
            case PACKET_DIAGNOSTICS:
                return "网络报错详情：数据包过大时，在日志中补充大小和来源信息，便于排查断线。\n" + "本项不扩大数据包容量，也不改变正常游戏内容；关闭后不再补充这些诊断信息。";
            default:
                throw new IllegalStateException("No player description for configurable feature: " + this);
        }
    }

    public boolean isEnabled() {
        boolean core = ApeironConfig.areAeMixinsEnabled();
        switch (this) {
            case TST_PATTERN:
                return ApeironConfig.isTstPatternEncodeGuardEnabled();
            case WAILA_DISPLAY:
                return core || ApeironConfig.isLightweightWailaEnabled();
            case WAILA_BUDGET:
                return ApeironConfig.isLightweightWailaEnabled();
            case PACKET_DIAGNOSTICS:
                return enabled;
            case EYE_OUTPUT:
                return core && ApeironConfig.isEyeOfHarmonyBigOutputEnabled();
            case TST_OUTPUT:
                return core && ApeironConfig.isTstBigOutputEnabled();
            case INFINITY_CELL:
                return core && ApeironConfig.isInfinityCellBigStorageEnabled();
            default:
                return core && enabled;
        }
    }

    public static MixinFeature of(String name) {
        String local = name.replace("com.silvia.apeiron.mixin.", "");
        if (local.startsWith("ae.")) return CORE;
        if (local.startsWith("gtnl.")) return GTNL;
        if (local.startsWith("gregtech.lanthanides.")) return BEAMLINE;
        if (local.startsWith("gregtech.quantum.")) return QUANTUM;
        if (local.startsWith("gregtech.spaceelevator.")) return SPACE_ELEVATOR;
        if (local.equals("gregtech.energy.NativeMachineWailaMixin") || local.startsWith("compat.omniocular."))
            return WAILA_DISPLAY;
        if (local.startsWith("gregtech.")) return CORE;
        if (local.startsWith("aeinfinitycell.")) return INFINITY_CELL;
        if (local.startsWith("tst.compat.")) return TST_PATTERN;
        if (local.startsWith("tst.")) return TST_OUTPUT;
        if (local.equals("tectech.EyeOfHarmonyEnhancementMixin")) return EYE_ENHANCEMENT;
        if (local.equals("tectech.EyeOfHarmonyBigOutputMixin")) return EYE_OUTPUT;
        if (local.startsWith("proghatches.")) return PROGRAMMABLE_HATCHES;
        if (local.startsWith("compat.waila.")) return WAILA_BUDGET;
        if (local.startsWith("network.")) return PACKET_DIAGNOSTICS;
        throw new IllegalArgumentException("Mixin has no configuration owner: " + name);
    }
}
