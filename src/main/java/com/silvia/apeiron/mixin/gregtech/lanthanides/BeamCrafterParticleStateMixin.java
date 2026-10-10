package com.silvia.apeiron.mixin.gregtech.lanthanides;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.api.machine.lanthanides.BeamItemInputController;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;

import gregtech.common.tileentities.machines.multi.beamcrafting.MTEBeamCrafter;

@Mixin(value = MTEBeamCrafter.class, remap = false)
public abstract class BeamCrafterParticleStateMixin implements BeamItemInputController {

    @Unique
    private boolean apeiron$itemParticleRecipe;

    @Shadow
    private int currentRecipeParticleIDA;
    @Shadow
    private int currentRecipeParticleIDB;

    @Override
    public void setItemParticleRecipe(boolean enabled) {
        apeiron$itemParticleRecipe = enabled;
    }

    @Override
    public void prepareItemParticleRecipe(int particleA, int particleB) {
        currentRecipeParticleIDA = particleA;
        currentRecipeParticleIDB = particleB;
        apeiron$itemParticleRecipe = true;
    }

    @Inject(method = "incrementProgressTime", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$completePaidParticles(CallbackInfo ci) {
        if (!apeiron$itemParticleRecipe) return;
        MTEBeamCrafter machine = (MTEBeamCrafter) (Object) this;
        // A native beam can supply the entire request in one update. Item mode paid it at recipe start.
        WirelessRecipeState state = ((BigWirelessController) machine).getWirelessRecipeState();
        if (state.isRunning() && !state.usesNativeEnergy()) machine.mProgresstime++;
        else machine.mProgresstime = machine.mMaxProgresstime;
        ci.cancel();
    }

    @Inject(method = "saveNBTData", at = @At("RETURN"), require = 1)
    private void apeiron$savePaidParticles(NBTTagCompound tag, CallbackInfo ci) {
        if (apeiron$itemParticleRecipe) tag.setBoolean("ApeironBeamItemRecipe", true);
        else tag.removeTag("ApeironBeamItemRecipe");
    }

    @Inject(method = "loadNBTData", at = @At("RETURN"), require = 1)
    private void apeiron$loadPaidParticles(NBTTagCompound tag, CallbackInfo ci) {
        apeiron$itemParticleRecipe = tag.getBoolean("ApeironBeamItemRecipe");
    }
}
