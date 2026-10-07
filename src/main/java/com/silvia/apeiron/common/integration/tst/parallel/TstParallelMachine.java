package com.silvia.apeiron.common.integration.tst.parallel;

import com.silvia.apeiron.api.machine.parallel.ParallelMachine;

/** Production machines with an INT_MAX parallel mode in TST 0.8.0-RC2.1. Entries do not install adapters. */
public enum TstParallelMachine implements ParallelMachine {

    INDUSTRIAL_MAGIC_MATRIX("GT_TileEntity_IndustrialMagicMatrix", "工业注魔矩阵", "需要英雄证明。"),
    MAGNETIC_MIXER("GT_TileEntity_MagneticMixer", "\"小型\"磁力搅拌机", ""),
    PHYSICAL_FORM_SWITCHER("GT_TileEntity_PhysicalFormSwitcher", "物质形态转换器", ""),
    SPACE_SCALER("GT_TileEntity_SpaceScaler", "空间缩放仪", ""),
    BALL_LIGHTNING("TST_BallLightning", "球状闪电", "仅无线模式或机器模式 3。"),
    HUMAN_POWER_CORE("TST_CoreDeviceOfHumanPowerGenerationFacility", "人类能源设施的核心装置", ""),
    DEPLOYED_NANO_CORE("TST_DeployedNanoCore", "展开的纳米核心", ""),
    HEPHAESTUS_ATELIER("TST_HephaestusAtelier", "赫菲斯托斯的工坊", ""),
    HYPER_THERMAL_CONVECTOR("TST_HyperThermalConvector", "高能态热对流器", ""),
    INDISTINCT_TENTACLE("TST_IndistinctTentacle", "不可视之触", "仅无线模式。"),
    INDUSTRIAL_ALCHEMY_TOWER("TST_IndustrialAlchemyTower", "工业炼金塔", "需要英雄证明。"),
    LARGE_CANNER("TST_LargeCanner", "大型装罐机", ""),
    INDUSTRIAL_COKING_FACTORY("TST_LargeIndustrialCokingFactory", "大型工业炼焦厂", ""),
    MEGA_MACERATOR("TST_MegaMacerator", "\"小型\"家用破壁机", "需要机壳档位 3。"),
    MEGA_STONE_BREAKER("TST_MegaStoneBreaker", "硅岩制造机", "需要总功率档位至少 29。"),
    MIRACLE_DOOR("TST_MiracleDoor", "奇迹之门", ""),
    SCAVENGER("TST_Scavenger", "拾荒者", ""),
    THERMAL_ENERGY_DEVOURER("TST_ThermalEnergyDevourer", "热能饕餮", ""),
    VACUUM_FILTER_EXTRACTOR("TST_VacuumFilterExtractor", "真空抽滤器", "");

    private final String key;
    private final String description;

    TstParallelMachine(final String key, final String name, final String condition) {
        this.key = key;
        this.description = name + "。 " + condition + " 适用候选：原生并行上限为 2,147,483,647 的模式。";
    }

    public String getKey() {
        return key;
    }

    public String getDescription() {
        return description;
    }
}
