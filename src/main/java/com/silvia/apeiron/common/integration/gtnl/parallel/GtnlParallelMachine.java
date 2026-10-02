package com.silvia.apeiron.common.integration.gtnl.parallel;

import java.math.BigInteger;
import java.util.Objects;

import com.silvia.apeiron.api.machine.parallel.ParallelMachine;

/** Explicit native unlimited candidates in GTNL 0.2.7-rc1 and the 2026-10-01 dev-290 source release. */
public enum GtnlParallelMachine implements ParallelMachine {

    INDUSTRIAL_ARCANE_ASSEMBLER("IndustrialArcaneAssembler", "工业奥术装配室", "需要保留奥术研究和配方完成逻辑。", Integer.MAX_VALUE),
    LAPOTRON_CHIP("LapotronChip", "兰波顿工厂", "", Integer.MAX_VALUE),
    TELEPORTATION_ARRAY_TO_ALFHEIM("TeleportationArrayToAlfheim", "精灵传送法阵", "需要保留魔力与催化剂条件。", Integer.MAX_VALUE),
    CHEAT_ORE_PROCESSING_FACTORY("CheatOreProcessingFactory", "作弊者的究极集成矿处", "使用独立无耗电矿处路径，保留原有耗电语义。", Integer.MAX_VALUE),
    NINE_INDUSTRIAL_MULTI_MACHINE("NineIndustrialMultiMachine", "大明科技", "需要按工作模式接入输出倍率与无线循环逻辑。", Integer.MAX_VALUE),
    ETERNAL_GREG_TECH_WORKSHOP("EternalGregTechWorkshop", "永恒格雷工坊", "控制器、模块、燃料与升级属于独立路径。", Integer.MAX_VALUE),
    ASSEMBLER_MATRIX("AssemblerMatrix", "装配矩阵", "仅含调试装配机壳、真实 long 上限为 LONG_MAX 的模式。需要独立样板适配。", Long.MAX_VALUE);

    private final String key;
    private final String description;
    private final BigInteger nativeUnlimitedMaximum;

    GtnlParallelMachine(final String key, final String name, final String condition, final long nativeMaximum) {
        this.key = key;
        nativeUnlimitedMaximum = BigInteger.valueOf(nativeMaximum);
        description = name + "。 "
            + condition
            + " 原生无限模式标记："
            + (nativeMaximum == Long.MAX_VALUE ? "LONG_MAX。" : "INT_MAX。");
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getDescription() {
        return description;
    }

    /** Compare the original field before any saturating int/long projection; finite tier values stay finite. */
    public boolean isNativeUnlimitedMode(final BigInteger nativeMaximum) {
        Objects.requireNonNull(nativeMaximum, "nativeMaximum");
        if (nativeMaximum.signum() < 0) throw new IllegalArgumentException("Negative native parallel maximum");
        return nativeUnlimitedMaximum.equals(nativeMaximum);
    }
}
