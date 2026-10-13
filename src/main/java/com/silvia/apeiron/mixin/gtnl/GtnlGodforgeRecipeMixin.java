package com.silvia.apeiron.mixin.gtnl;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.parallel.ExactProcessingContext;

import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.misc.WirelessNetworkManager;

/** GTNL modules have their own prediction and startup hooks even with modern GregTech installed. */
@Pseudo
@Mixin(
    targets = { "com.science.gtnl.common.machine.multiblock.FOGAlloyBlastSmelterModule$1",
        "com.science.gtnl.common.machine.multiblock.FOGAlloySmelterModule$1",
        "com.science.gtnl.common.machine.multiblock.FOGExtractorModule$1",
        "com.science.gtnl.common.machine.multiblock.FOGSolarMuonCatalystModule$1" },
    remap = false)
public abstract class GtnlGodforgeRecipeMixin extends ProcessingLogic {

    @ModifyExpressionValue(
        method = "validateRecipe",
        at = @At(value = "INVOKE", target = "Ljava/math/BigInteger;compareTo(Ljava/math/BigInteger;)I", ordinal = 0),
        require = 1)
    private int apeiron$estimate(int comparison) {
        // Keep voltage and upgrade checks; the exact planner checks the discounted cost and affordable count.
        return machine instanceof MTEMultiBlockBase && InfiniteEnergyHatches.find((MTEMultiBlockBase) machine) != null
            ? 0
            : comparison;
    }

    @Redirect(
        method = "onRecipeStart",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/common/misc/WirelessNetworkManager;addEUToGlobalEnergyMap(Ljava/util/UUID;J)Z"),
        require = 1)
    private boolean apeiron$debit(UUID owner, long amount) {
        // Retain heat and milestone side effects. Only the prepared exact recipe owns its energy debit schedule.
        return machine instanceof MTEMultiBlockBase && ExactProcessingContext.isStarting((MTEMultiBlockBase) machine)
            || WirelessNetworkManager.addEUToGlobalEnergyMap(owner, amount);
    }
}
