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
        com.silvia.apeiron.config.MixinFeature feature = com.silvia.apeiron.config.MixinFeature.of(mixinClassName);
        if (!feature.isEnabled()) return false;
        // Vanilla resources may still use obfuscated paths here; Mixin's refmap resolves this mandatory target.
        if (feature == com.silvia.apeiron.config.MixinFeature.PACKET_DIAGNOSTICS) return true;
        if (!DependencyCapabilities.hasClass(targetClassName)) return false;
        String name = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        switch (name) {
            case "AEBaseCellContentsMixin":
                return DependencyCapabilities.invokes(
                    "appeng.items.AEBaseCell",
                    "addCheckedInformation",
                    "appeng/api/storage/data/IAEStack",
                    "getStackSize");
            case "LegacyTaskProgressAccessMixin":
            case "ModernTaskProgressAccessMixin":
                return DependencyCapabilities
                    .hasMethod("appeng.me.cluster.implementations.CraftingCPUCluster$TaskProgress", "access$200", null)
                    == name.startsWith("Legacy");
            case "TargetChamberInputMixin":
            case "SourceChamberInputMixin":
                return DependencyCapabilities
                    .hasMethod("gtnhlanth.common.tileentity.MTETargetChamber", "getMaskItemStack", null);
            case "GtnlWirelessBatchMixin":
                return DependencyCapabilities.hasMethod(targetClassName, "checkProcessing", null);
            case "GtnlWirelessStepMixin":
                return DependencyCapabilities.hasMethod(targetClassName, "wirelessModeProcessOnce", null);
            case "ModernNativeFluidOutputBatchMixin":
            case "LegacyNativeFluidOutputBatchMixin":
                return DependencyCapabilities
                    .hasMethod(targetClassName, "addFluidOutputs", "([Lnet/minecraftforge/fluids/FluidStack;)Z")
                    == name.startsWith("Modern");
            case "LegacyModuleParallelGuiMixin":
                return DependencyCapabilities.hasMethod(targetClassName, "createInputWidget", null);
            case "LegacyOptimizerTaskMixin":
            case "ModernOptimizerTaskMixin":
                return DependencyCapabilities.hasMethod(
                    targetClassName,
                    "addCraftingTask",
                    "(Lappeng/api/networking/crafting/ICraftingPatternDetails;J)V") == name.startsWith("Modern");
            case "LegacyCraftingEntrypointMixin":
            case "ModernCraftingEntrypointMixin":
                return DependencyCapabilities.hasClass("appeng.crafting.fast.CraftingJobFast")
                    == name.startsWith("Modern");
            case "NativeInputWatcherMixin":
                return DependencyCapabilities.hasMethod(targetClassName, "addWatcher", null)
                    && DependencyCapabilities.hasMethod(targetClassName, "removeWatcher", null);
            case "BigInputHatchElementMixin":
                return DependencyCapabilities.hasMethod(targetClassName, "mteClasses", null);
            case "LegacyBigInputHatchElementMixin":
                return !DependencyCapabilities.hasMethod("gregtech.api.enums.HatchElement$3", "mteClasses", null);
            case "ModernMEOutputHatchMixin":
                return DependencyCapabilities.hasClass("gregtech.api.interfaces.IOutputHatch");
            case "NativeMEOutputCapacityMixin":
                return DependencyCapabilities.hasMethod(targetClassName, "canDumpItemToME", "(Ljava/util/List;)Z")
                    && DependencyCapabilities.hasMethod(targetClassName, "canDumpFluidToME", "(Ljava/util/List;)Z");
            case "LegacyNativeMEOutputCapacityMixin":
                return DependencyCapabilities.hasMethod(targetClassName, "canDumpFluidToME", "()Z")
                    && !DependencyCapabilities.hasMethod(targetClassName, "canDumpFluidToME", "(Ljava/util/List;)Z");
            case "LegacyReshuffleTaskMixin":
            case "ReshuffleTaskMixin":
                return DependencyCapabilities.hasClass("appeng.helpers.ReshuffleTask$PendingInjection")
                    == name.equals("ReshuffleTaskMixin");
            case "CraftingTreeSerializerBigMixin":
                return DependencyCapabilities.hasMethod(targetClassName, "writeStackWithSize", null);
            case "PatternInputOptimizationMixin":
                return DependencyCapabilities.hasClass("reobf.proghatches.gt.metatileentity.util.ISpecialOptimize");
            case "TstPatternEncodeGuardMixin":
                return DependencyCapabilities
                    .hasClass("com.Nxer.TwistSpaceTechnology.mixin.MixinContainerPatternTermEncode");
            case "LegacyTstOutputQueueMixin":
                return DependencyCapabilities
                    .hasMethod(targetClassName, "replaceMEOutputQueues", "(Ljava/util/List;Ljava/util/List;)V");
            case "ModernTstOutputQueueMixin":
                return DependencyCapabilities.hasMethod(targetClassName, "mergeOutputItems", "(Ljava/util/List;)V");
            case "WirelessOutputBigMixin":
                return DependencyCapabilities.hasMethod(targetClassName, "mergeWirelessOutputsIntoMEQueue", "()V");
            case "StructurePreviewItemSourceMixin":
                return DependencyCapabilities.hasClass(targetClassName)
                    && DependencyCapabilities.hasClass("codechicken.nei.ItemList")
                    && DependencyCapabilities.hasClass("gregtech.common.blocks.ItemMachines");
            default:
                return true;
        }
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
