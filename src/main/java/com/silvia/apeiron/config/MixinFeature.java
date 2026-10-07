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
            "Enable the complete " + feature.name()
                + " integration. Requires a restart; keep client and server settings equal.");
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
