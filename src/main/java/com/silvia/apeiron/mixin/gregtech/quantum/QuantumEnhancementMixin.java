package com.silvia.apeiron.mixin.gregtech.quantum;

import java.util.ArrayList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.silvia.apeiron.api.machine.quantum.EnhancedQuantumController;
import com.silvia.apeiron.common.machine.quantum.MTEQuantumEnhancementModule;

import gregtech.api.metatileentity.implementations.MTEHatchBulkCatalystHousing;
import gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.production.MTEQuantumForceTransformer;

@Mixin(value = MTEQuantumForceTransformer.class, remap = false)
public abstract class QuantumEnhancementMixin implements EnhancedQuantumController {

    @Shadow
    private ArrayList<MTEHatchBulkCatalystHousing> catalystHousings;
    @Shadow
    protected int mCraftingTier;
    @Shadow
    protected int mMaxParallel;
    @Shadow
    private boolean mFluidMode;
    @Shadow
    private boolean doFermium;
    @Shadow
    private boolean doNeptunium;

    @Override
    public boolean hasQuantumEnhancement() {
        for (MTEHatchBulkCatalystHousing hatch : catalystHousings)
            if (hatch instanceof MTEQuantumEnhancementModule && hatch.isValid()) return true;
        return false;
    }

    @Override
    public int getQuantumCraftingTier() {
        return mCraftingTier;
    }

    @Override
    public boolean isQuantumFluidMode() {
        return mFluidMode;
    }

    @Override
    public void prepareEnhancedQuantumRecipe(int parallels) {
        mMaxParallel = parallels;
        // All outputs are guaranteed, so focus plasma must not be consumed or cancel the finished outputs.
        doFermium = false;
        doNeptunium = false;
    }
}
