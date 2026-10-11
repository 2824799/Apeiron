package com.silvia.apeiron.mixin.gregtech.energy;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import tectech.thing.metaTileEntity.multi.godforge.MTEBaseModule;
import tectech.thing.metaTileEntity.multi.godforge.util.ForgeOfGodsData;
import tectech.thing.metaTileEntity.multi.godforge.util.GodforgeMath;

/** Native milestone counters must not wrap and erase the main controller's upgrades after a big batch. */
@Mixin(value = GodforgeMath.class, remap = false)
public abstract class GodforgeMilestoneMixin {

    @Redirect(
        method = "queryMilestoneStats",
        at = @At(
            value = "INVOKE",
            target = "Ltectech/thing/metaTileEntity/multi/godforge/util/ForgeOfGodsData;setTotalRecipesProcessed(J)V"),
        require = 1)
    private static void apeiron$recipeTally(ForgeOfGodsData data, long nativeTotal, MTEBaseModule module,
        ForgeOfGodsData source) {
        long previous = data.getTotalRecipesProcessed(), added = module.getRecipeTally();
        data.setTotalRecipesProcessed(
            previous >= 0 && added >= 0 && added > Long.MAX_VALUE - previous ? Long.MAX_VALUE : nativeTotal);
    }

    @Redirect(
        method = "determineChargeMilestone",
        at = @At(value = "INVOKE", target = "Ljava/lang/Math;log(D)D"),
        require = 1)
    private static double apeiron$powerProgress(double nativeValue, ForgeOfGodsData data) {
        BigInteger ratio = data.getTotalPowerConsumed()
            .divide(BigInteger.valueOf(ForgeOfGodsData.POWER_MILESTONE_CONSTANT));
        if (ratio.bitLength() < 64) return Math.log(nativeValue);
        int shift = ratio.bitLength() - 53;
        return Math.log(
            ratio.shiftRight(shift)
                .doubleValue())
            + shift * Math.log(2);
    }
}
