package com.silvia.apeiron.mixin.gregtech.purification;

import java.math.BigInteger;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.silvia.apeiron.api.machine.parallel.BigPurificationPlant;
import com.silvia.apeiron.api.machine.parallel.BigPurificationUnit;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.parallel.PurificationWaterInputs;

import gregtech.common.tileentities.machines.multi.purification.LinkedPurificationUnit;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationPlant;

/** Preserve the plant's cycle and active-unit list; only replace its overflowing power accumulator. */
@Mixin(value = MTEPurificationPlant.class, remap = false)
public abstract class PurificationPlantPowerMixin implements BigPurificationPlant {

    @Shadow
    private List<LinkedPurificationUnit> linkedUnits;
    @Unique
    private BigInteger apeiron$cyclePower;

    @Override
    public BigInteger getPurificationPowerUsageBig() {
        if (apeiron$cyclePower != null && ((MTEPurificationPlant) (Object) this).mMaxProgresstime > 0)
            return apeiron$cyclePower;
        return apeiron$collectPower();
    }

    @Unique
    private BigInteger apeiron$collectPower() {
        BigInteger power = BigInteger.ZERO;
        for (LinkedPurificationUnit link : linkedUnits)
            if (link.isActive() && link.metaTileEntity().mMaxProgresstime > 0)
                power = power.add(((BigPurificationUnit) link.metaTileEntity()).getPurificationPowerUsageBig());
        return power;
    }

    @Inject(method = "startCycle", at = @At("HEAD"), require = 1)
    private void apeiron$begin(CallbackInfo ci) {
        apeiron$cyclePower = null;
    }

    @WrapMethod(method = "calculateEffectivePowerUsage")
    private long apeiron$power(Operation<Long> original) {
        MTEPurificationPlant plant = (MTEPurificationPlant) (Object) this;
        if (InfiniteEnergyHatches.find(plant) == null || PurificationWaterInputs.usesSplitCycleOutputs(plant))
            return original.call();
        apeiron$cyclePower = apeiron$collectPower();
        return apeiron$cyclePower.min(BigInteger.valueOf(Long.MAX_VALUE))
            .longValue();
    }

    @Inject(method = "saveNBTData", at = @At("RETURN"), require = 1)
    private void apeiron$save(NBTTagCompound tag, CallbackInfo ci) {
        MTEPurificationPlant plant = (MTEPurificationPlant) (Object) this;
        if (plant.mMaxProgresstime > 0 && InfiniteEnergyHatches.find(plant) != null
            && !PurificationWaterInputs.usesSplitCycleOutputs(plant))
            tag.setString("ApeironPurificationPower", getPurificationPowerUsageBig().toString());
        else tag.removeTag("ApeironPurificationPower");
    }

    @Inject(method = "loadNBTData", at = @At("RETURN"), require = 1)
    private void apeiron$load(NBTTagCompound tag, CallbackInfo ci) {
        // Native units relink later in onPostTick. Keep paying the saved full cycle in the meantime.
        apeiron$cyclePower = tag.hasKey("ApeironPurificationPower")
            ? new BigInteger(tag.getString("ApeironPurificationPower"))
            : ((MTEPurificationPlant) (Object) this).mMaxProgresstime > 0
                ? BigInteger.valueOf(((MTEPurificationPlant) (Object) this).lEUt)
                    .abs()
                : null;
    }
}
