package com.silvia.apeiron.mixin.gregtech.purification;

import java.math.BigDecimal;
import java.math.BigInteger;

import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.api.machine.parallel.BigPurificationUnit;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.compat.OutputTransactions;

import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitDegasser;

/** The degasser's native output bonus uses the exact cycle water rather than its int GUI projection. */
@Mixin(value = MTEPurificationUnitDegasser.class, remap = false)
public abstract class PurificationDegasserOutputMixin {

    @Shadow
    private float outputMultiplier;
    @Unique
    private float apeiron$nativeMultiplier;
    @Unique
    private boolean apeiron$exactBonus;

    @Inject(method = "addRecipeOutputs", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$prepareBonus(CallbackInfo ci) {
        MTEPurificationUnitDegasser unit = (MTEPurificationUnitDegasser) (Object) this;
        if (((BigPurificationUnit) unit).getPurificationWaterOutputBig()
            .signum() > 0 && !((BigPurificationUnit) unit).isPurificationOutputPendingBig()) {
            ci.cancel();
            return;
        }
        apeiron$exactBonus = outputMultiplier > 1.01f && unit.mOutputFluids != null
            && ((BigPurificationUnit) unit).getPurificationWaterOutputBig()
                .signum() > 0;
        if (!apeiron$exactBonus) return;
        apeiron$nativeMultiplier = outputMultiplier;
        // Let the native super call output the base cycle, then supply the exact bonus below.
        outputMultiplier = 1.0f;
    }

    @Inject(method = "addRecipeOutputs", at = @At("RETURN"), require = 1)
    private void apeiron$bonus(CallbackInfo ci) {
        if (!apeiron$exactBonus) return;
        apeiron$exactBonus = false;
        outputMultiplier = apeiron$nativeMultiplier;
        MTEPurificationUnitDegasser unit = (MTEPurificationUnitDegasser) (Object) this;
        FluidStack output = unit.mOutputFluids[0];
        BigInteger water = ((BigPurificationUnit) unit).getPurificationWaterOutputBig();
        BigInteger amount = new BigDecimal(water).multiply(BigDecimal.valueOf(outputMultiplier - 1.0d))
            .toBigInteger();
        ((BigWirelessController) unit).getWirelessRecipeState()
            .pending()
            .addFluid(output, amount);
        ((BigWirelessController) unit).getWirelessRecipeState()
            .pending()
            .flush(
                unit.getOutputBusses(),
                OutputTransactions.hatches(unit),
                unit.protectsExcessItem(),
                unit.protectsExcessFluid());
        unit.markDirty();
    }
}
