package com.silvia.apeiron.mixin.config;

import java.io.File;
import java.util.List;
import java.util.Set;

import net.minecraft.launchwrapper.Launch;

import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import com.silvia.apeiron.compat.DependencyCapabilities;
import com.silvia.apeiron.config.ApeironConfig;

/** Selects integrations using configuration grouped by the providing mod. */
public final class ApeironMixinConfigPlugin implements IMixinConfigPlugin {

    private static final String AE_MIXIN_PREFIX = "com.silvia.apeiron.mixin.ae.";
    private static final String EYE_OUTPUT_MIXIN = "com.silvia.apeiron.mixin.tectech.EyeOfHarmonyBigOutputMixin";
    private static final String EYE_ENHANCEMENT_MIXIN = "com.silvia.apeiron.mixin.tectech.EyeOfHarmonyEnhancementMixin";
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
        if (mixinClassName.startsWith("com.silvia.apeiron.mixin.gregtech.lanthanides."))
            return ApeironConfig.areAeMixinsEnabled() && DependencyCapabilities
                .hasMethod("gtnhlanth.common.tileentity.MTETargetChamber", "getMaskItemStack", null);
        if (mixinClassName.startsWith("com.silvia.apeiron.mixin.gtnl.")) {
            if (!ApeironConfig.areAeMixinsEnabled() || !DependencyCapabilities.hasClass(targetClassName)) return false;
            if (mixinClassName.endsWith(".GtnlWirelessBatchMixin"))
                return DependencyCapabilities.hasMethod(targetClassName, "checkProcessing", null);
            if (mixinClassName.endsWith(".GtnlWirelessStepMixin"))
                return DependencyCapabilities.hasMethod(targetClassName, "wirelessModeProcessOnce", null);
            return true;
        }
        if (mixinClassName.startsWith("com.silvia.apeiron.mixin.gregtech.quantum."))
            return ApeironConfig.areAeMixinsEnabled() && DependencyCapabilities.hasClass(targetClassName);
        if (mixinClassName.endsWith(".TecTechMixedOutputRegistrationMixin")
            || mixinClassName.endsWith(".GtppMixedOutputRegistrationMixin"))
            return ApeironConfig.areAeMixinsEnabled() && DependencyCapabilities.hasClass(targetClassName);
        if (mixinClassName.endsWith(".NativeOutputBatchMixin")) return ApeironConfig.areAeMixinsEnabled();
        if (mixinClassName.endsWith(".ModernNativeFluidOutputBatchMixin")
            || mixinClassName.endsWith(".LegacyNativeFluidOutputBatchMixin")) {
            boolean modern = DependencyCapabilities
                .hasMethod(targetClassName, "addFluidOutputs", "([Lnet/minecraftforge/fluids/FluidStack;)Z");
            return ApeironConfig.areAeMixinsEnabled()
                && modern == mixinClassName.endsWith(".ModernNativeFluidOutputBatchMixin");
        }
        if (mixinClassName.endsWith(".LegacyModuleParallelGuiMixin")) return ApeironConfig.areAeMixinsEnabled()
            && DependencyCapabilities.hasMethod(targetClassName, "createInputWidget", null);
        if (mixinClassName.startsWith("com.silvia.apeiron.mixin.gregtech.spaceelevator."))
            return ApeironConfig.areAeMixinsEnabled() && DependencyCapabilities.hasClass(targetClassName);
        if (mixinClassName.endsWith(".LegacyOptimizerTaskMixin")
            || mixinClassName.endsWith(".ModernOptimizerTaskMixin")) {
            final boolean modern = DependencyCapabilities.hasMethod(
                targetClassName,
                "addCraftingTask",
                "(Lappeng/api/networking/crafting/ICraftingPatternDetails;J)V");
            return ApeironConfig.areAeMixinsEnabled() && modern == mixinClassName.endsWith(".ModernOptimizerTaskMixin");
        }
        if (mixinClassName.endsWith(".LegacyCraftingEntrypointMixin")
            || mixinClassName.endsWith(".ModernCraftingEntrypointMixin")) {
            boolean modern = DependencyCapabilities.hasClass("appeng.crafting.fast.CraftingJobFast");
            return ApeironConfig.areAeMixinsEnabled()
                && (modern == mixinClassName.endsWith(".ModernCraftingEntrypointMixin"));
        }
        if (mixinClassName.endsWith(".NativeInputWatcherMixin")) return ApeironConfig.areAeMixinsEnabled()
            && DependencyCapabilities.hasMethod(targetClassName, "addWatcher", null)
            && DependencyCapabilities.hasMethod(targetClassName, "removeWatcher", null);
        if (mixinClassName.endsWith(".BigInputHatchElementMixin")) {
            return ApeironConfig.areAeMixinsEnabled()
                && DependencyCapabilities.hasMethod(targetClassName, "mteClasses", null);
        }
        if (mixinClassName.endsWith(".LegacyBigInputHatchElementMixin")) {
            return ApeironConfig.areAeMixinsEnabled()
                && DependencyCapabilities.hasClass("gregtech.api.enums.HatchElement")
                && !DependencyCapabilities.hasMethod("gregtech.api.enums.HatchElement$3", "mteClasses", null);
        }
        if (mixinClassName.endsWith(".ModernMEOutputHatchMixin")) return ApeironConfig.areAeMixinsEnabled()
            && DependencyCapabilities.hasClass("gregtech.api.interfaces.IOutputHatch");
        if (mixinClassName.endsWith(".NativeMEOutputCapacityMixin")) return ApeironConfig.areAeMixinsEnabled()
            && DependencyCapabilities.hasMethod(targetClassName, "canDumpItemToME", "(Ljava/util/List;)Z")
            && DependencyCapabilities.hasMethod(targetClassName, "canDumpFluidToME", "(Ljava/util/List;)Z");
        if (mixinClassName.endsWith(".LegacyNativeMEOutputCapacityMixin")) return ApeironConfig.areAeMixinsEnabled()
            && DependencyCapabilities.hasMethod(targetClassName, "canDumpFluidToME", "()Z")
            && !DependencyCapabilities.hasMethod(targetClassName, "canDumpFluidToME", "(Ljava/util/List;)Z");
        if (mixinClassName.endsWith(".LegacyReshuffleTaskMixin")) return ApeironConfig.areAeMixinsEnabled()
            && !DependencyCapabilities.hasClass("appeng.helpers.ReshuffleTask$PendingInjection");
        if (mixinClassName.endsWith(".ReshuffleTaskMixin")) return ApeironConfig.areAeMixinsEnabled()
            && DependencyCapabilities.hasClass("appeng.helpers.ReshuffleTask$PendingInjection");
        if (mixinClassName.endsWith(".CraftingTreeSerializerBigMixin")) return ApeironConfig.areAeMixinsEnabled()
            && DependencyCapabilities.hasMethod(targetClassName, "writeStackWithSize", null);
        if (mixinClassName.startsWith(AE_MIXIN_PREFIX) && !DependencyCapabilities.hasClass(targetClassName))
            return false;
        if (mixinClassName.startsWith("com.silvia.apeiron.mixin.compat.omniocular."))
            return (ApeironConfig.areAeMixinsEnabled() || ApeironConfig.isLightweightWailaEnabled())
                && getClass().getClassLoader()
                    .getResource("me/exz/omniocular/waila/TileEntityHandler.class") != null;
        if (mixinClassName.startsWith("com.silvia.apeiron.mixin.compat.waila."))
            return ApeironConfig.isLightweightWailaEnabled() && getClass().getClassLoader()
                .getResource(targetClassName.replace('.', '/') + ".class") != null;
        if (mixinClassName.startsWith("com.silvia.apeiron.mixin.proghatches.")) {
            return ApeironConfig.areAeMixinsEnabled() && getClass().getClassLoader()
                .getResource("reobf/proghatches/gt/metatileentity/util/ISpecialOptimize.class") != null;
        }
        if (mixinClassName.startsWith("com.silvia.apeiron.mixin.gregtech.energy.")) {
            if (mixinClassName.endsWith(".NativeMachineWailaMixin"))
                return ApeironConfig.isLightweightWailaEnabled() || ApeironConfig.areAeMixinsEnabled();
            if (mixinClassName.contains("Godforge") || mixinClassName.contains("TecTech"))
                return ApeironConfig.areAeMixinsEnabled() && getClass().getClassLoader()
                    .getResource(targetClassName.replace('.', '/') + ".class") != null;
            return ApeironConfig.areAeMixinsEnabled() && getClass().getClassLoader()
                .getResource(targetClassName.replace('.', '/') + ".class") != null;
        }
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
            if (mixinClassName.endsWith(".LegacyTstOutputQueueMixin") && !DependencyCapabilities
                .hasMethod(targetClassName, "replaceMEOutputQueues", "(Ljava/util/List;Ljava/util/List;)V"))
                return false;
            if (mixinClassName.endsWith(".ModernTstOutputQueueMixin")
                && !DependencyCapabilities.hasMethod(targetClassName, "mergeOutputItems", "(Ljava/util/List;)V"))
                return false;
            if (mixinClassName.endsWith(".WirelessOutputBigMixin")
                && !DependencyCapabilities.hasMethod(targetClassName, "mergeWirelessOutputsIntoMEQueue", "()V"))
                return false;
            return ApeironConfig.isTstBigOutputEnabled() && ApeironConfig.areAeMixinsEnabled()
                && getClass().getClassLoader()
                    .getResource(
                        "com/Nxer/TwistSpaceTechnology/common/machine/multiMachineClasses/GTCM_MultiMachineBase.class")
                    != null
                && getClass().getClassLoader()
                    .getResource(targetClassName.replace('.', '/') + ".class") != null;
        }
        if (EYE_OUTPUT_MIXIN.equals(mixinClassName)) {
            return ApeironConfig.isEyeOfHarmonyBigOutputEnabled() && ApeironConfig.areAeMixinsEnabled();
        }
        if (EYE_ENHANCEMENT_MIXIN.equals(mixinClassName)) {
            return ApeironConfig.areAeMixinsEnabled() && getClass().getClassLoader()
                .getResource("tectech/thing/metaTileEntity/multi/MTEEyeOfHarmony.class") != null;
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
