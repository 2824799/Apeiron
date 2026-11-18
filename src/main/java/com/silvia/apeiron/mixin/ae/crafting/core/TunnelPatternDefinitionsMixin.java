package com.silvia.apeiron.mixin.ae.crafting.core;

import java.util.Map;
import java.util.UUID;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.google.common.collect.ImmutableMap;
import com.silvia.apeiron.crafting.TunnelPatternInputs;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.me.cache.CraftingGridCache;

@Mixin(value = CraftingGridCache.class, remap = false)
public abstract class TunnelPatternDefinitionsMixin implements TunnelPatternInputs.Definitions {

    @Shadow
    @Final
    protected Map<UUID, ICraftingPatternDetails> inputOnlyPatterns;

    @Unique
    private volatile Map<UUID, ICraftingPatternDetails> apeiron$tunnelPatterns = ImmutableMap.of();

    @Inject(method = "setPatternsFromCraftingMethods", at = @At("RETURN"))
    private void apeiron$snapshotTunnels(CallbackInfo ci) {
        apeiron$tunnelPatterns = ImmutableMap.copyOf(inputOnlyPatterns);
    }

    @Override
    public Map<UUID, ICraftingPatternDetails> getTunnelPatterns() {
        return apeiron$tunnelPatterns;
    }
}
