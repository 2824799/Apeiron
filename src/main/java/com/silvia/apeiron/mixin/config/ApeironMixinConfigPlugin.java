package com.silvia.apeiron.mixin.config;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import com.silvia.apeiron.config.ApeironConfig;

/** Applies the single configuration switch for all Apeiron AE Mixins. */
public final class ApeironMixinConfigPlugin implements IMixinConfigPlugin {

    private static final String AE_MIXIN_PREFIX = "com.silvia.apeiron.mixin.ae.";

    @Override
    public void onLoad(final String mixinPackage) {
        ApeironConfig.load(new File("config", ApeironConfig.FILE_NAME));
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        return !mixinClassName.startsWith(AE_MIXIN_PREFIX) || ApeironConfig.areAeMixinsEnabled();
    }

    @Override
    public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(
        final String targetClassName,
        final ClassNode targetClass,
        final String mixinClassName,
        final IMixinInfo mixinInfo) {}

    @Override
    public void postApply(
        final String targetClassName,
        final ClassNode targetClass,
        final String mixinClassName,
        final IMixinInfo mixinInfo) {}
}
