package com.silvia.apeiron.mixin.gregtech.energy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;

import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Legacy modules compare an undiscounted estimate before the exact planner can check the real batch cost. */
@Pseudo
@Mixin(
    targets = { "tectech.thing.metaTileEntity.multi.godforge.MTESmeltingModule$1",
        "tectech.thing.metaTileEntity.multi.godforge.MTEMoltenModule$1",
        "tectech.thing.metaTileEntity.multi.godforge.MTEPlasmaModule$1",
        "tectech.thing.metaTileEntity.multi.godforge.MTEExoticModule$1" },
    remap = false)
public abstract class LegacyGodforgeRecipePredictionMixin extends ProcessingLogic {

    @ModifyExpressionValue(
        method = "validateRecipe",
        at = @At(value = "INVOKE", target = "Ljava/math/BigInteger;compareTo(Ljava/math/BigInteger;)I", ordinal = 0),
        require = 1)
    private int apeiron$estimate(int comparison) {
        // Only this preliminary estimate is deferred; heat/tier checks and actual budget/debit remain in place.
        return machine instanceof MTEMultiBlockBase && InfiniteEnergyHatches.find((MTEMultiBlockBase) machine) != null
            ? 0
            : comparison;
    }
}
