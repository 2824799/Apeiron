package com.silvia.apeiron.mixin.config;

import java.io.File;
import java.util.List;
import java.util.Set;

import net.minecraft.launchwrapper.Launch;

import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import com.silvia.apeiron.config.ApeironConfig;

/** Selects integrations using configuration grouped by the providing mod. */
public final class ApeironMixinConfigPlugin implements IMixinConfigPlugin {

    private static final String AE_MIXIN_PREFIX = "com.silvia.apeiron.mixin.ae.";
    private static final String EYE_OUTPUT_MIXIN = "com.silvia.apeiron.mixin.tectech.EyeOfHarmonyBigOutputMixin";
    private static final String TST_MIXIN_PREFIX = "com.silvia.apeiron.mixin.tst.";
    private static final String INFINITY_CELL_MIXIN_PREFIX = "com.silvia.apeiron.mixin.aeinfinitycell.";

    @Override
    public void onLoad(final String mixinPackage) {
        ApeironConfig
            .loadFromDirectory(new File(Launch.minecraftHome == null ? new File(".") : Launch.minecraftHome, "config"));
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        if (mixinClassName.startsWith("com.silvia.apeiron.mixin.compat.omniocular."))
            return ApeironConfig.areAeMixinsEnabled() && getClass().getClassLoader()
                .getResource("me/exz/omniocular/waila/TileEntityHandler.class") != null;
        if (mixinClassName.startsWith("com.silvia.apeiron.mixin.proghatches.")) {
            return ApeironConfig.areAeMixinsEnabled() && getClass().getClassLoader()
                .getResource("reobf/proghatches/gt/metatileentity/util/ISpecialOptimize.class") != null;
        }
        if (mixinClassName.startsWith("com.silvia.apeiron.mixin.gregtech.energy."))
            return ApeironConfig.areAeMixinsEnabled();
        if (mixinClassName.equals("com.silvia.apeiron.mixin.tst.compat.TstPatternEncodeGuardMixin")) {
            return ApeironConfig.isTstPatternEncodeGuardEnabled() && getClass().getClassLoader()
                .getResource("com/Nxer/TwistSpaceTechnology/mixin/MixinContainerPatternTermEncode.class") != null;
        }
        if (mixinClassName.startsWith(INFINITY_CELL_MIXIN_PREFIX)) {
            return ApeironConfig.isInfinityCellBigStorageEnabled() && ApeironConfig.areAeMixinsEnabled()
                && getClass().getClassLoader()
                    .getResource("cn/dancingsnow/aeinfinitycell/storage/InfinityCellRecord.class") != null
                && getClass().getClassLoader()
                    .getResource(targetClassName.replace('.', '/') + ".class") != null;
        }
        if (mixinClassName.startsWith(TST_MIXIN_PREFIX)) {
            return ApeironConfig.isTstBigOutputEnabled() && ApeironConfig.areAeMixinsEnabled()
                && getClass().getClassLoader()
                    .getResource(
                        "com/Nxer/TwistSpaceTechnology/common/machine/multiMachineClasses/GTCM_MultiMachineBase.class")
                    != null;
        }
        if (EYE_OUTPUT_MIXIN.equals(mixinClassName)) {
            return ApeironConfig.isEyeOfHarmonyBigOutputEnabled() && ApeironConfig.areAeMixinsEnabled();
        }
        return !mixinClassName.startsWith(AE_MIXIN_PREFIX) || ApeironConfig.areAeMixinsEnabled();
    }

    @Override
    public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName,
        final IMixinInfo mixinInfo) {}

    @Override
    public void postApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName,
        final IMixinInfo mixinInfo) {}
}
