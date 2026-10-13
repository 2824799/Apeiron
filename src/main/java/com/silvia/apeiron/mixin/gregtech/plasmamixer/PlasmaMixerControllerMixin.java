package com.silvia.apeiron.mixin.gregtech.plasmamixer;

import java.math.BigInteger;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.api.machine.parallel.BigPlasmaMixerController;
import com.silvia.apeiron.api.machine.parallel.ParallelLimit;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigValueCodec;

import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.common.tileentities.machines.multi.MTETranscendentPlasmaMixer;

@Mixin(value = MTETranscendentPlasmaMixer.class, remap = false)
public abstract class PlasmaMixerControllerMixin extends MTEEnhancedMultiBlockBase<MTETranscendentPlasmaMixer>
    implements BigPlasmaMixerController {

    @Shadow
    private UUID ownerUUID;
    @Shadow
    private int multiplier;
    @Shadow
    BigInteger finalConsumption;
    @Unique
    private BigInteger apeiron$parallel = BigInteger.valueOf(Integer.MAX_VALUE);
    @Unique
    private int apeiron$duration = 1;

    protected PlasmaMixerControllerMixin(String name) {
        super(name);
    }

    @Override
    public BigInteger getPlasmaMixerParallel() {
        return apeiron$parallel;
    }

    @Override
    public void setPlasmaMixerParallel(BigInteger value) {
        if (value.signum() < 0) throw new IllegalArgumentException("Negative plasma mixer parallel");
        apeiron$parallel = value;
        multiplier = value.signum() == 0 ? Integer.MAX_VALUE
            : value.min(BigInteger.valueOf(Integer.MAX_VALUE))
                .intValue();
        markDirty();
    }

    @Override
    public int getPlasmaMixerDuration() {
        return apeiron$duration;
    }

    @Override
    public void setPlasmaMixerDuration(int ticks) {
        if (ticks < 1) throw new IllegalArgumentException("Invalid plasma mixer working time");
        apeiron$duration = ticks;
        markDirty();
    }

    @Override
    public UUID getPlasmaMixerOwner() {
        return ownerUUID;
    }

    @Override
    public void recordPlasmaMixerEnergy(BigInteger energy) {
        finalConsumption = energy.negate();
    }

    @Override
    public ParallelLimit getParallelLimitBig() {
        return apeiron$parallel.signum() == 0 ? ParallelLimit.unlimited() : ParallelLimit.bounded(apeiron$parallel);
    }

    @Inject(method = { "getMultiplier", "getMaxParallelRecipes" }, at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$legacyView(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(
            apeiron$parallel.signum() == 0 ? Integer.MAX_VALUE
                : apeiron$parallel.min(BigInteger.valueOf(Integer.MAX_VALUE))
                    .intValue());
    }

    @Inject(method = "setMultiplier", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$legacyEdit(int value, CallbackInfo ci) {
        if (value >= 0) setPlasmaMixerParallel(BigInteger.valueOf(value));
        ci.cancel();
    }

    @Inject(method = "saveNBTData", at = @At("RETURN"), require = 1)
    private void apeiron$save(NBTTagCompound tag, CallbackInfo ci) {
        NBTTagCompound settings = new NBTTagCompound();
        BigValueCodec.writeNBT(settings, "parallel", "parallelBig", new AdaptiveInteger(apeiron$parallel));
        settings.setInteger("duration", apeiron$duration);
        tag.setTag("ApeironPlasmaMixer", settings);
        tag.setInteger("eMultiplier", ((MTETranscendentPlasmaMixer) (Object) this).getMultiplier());
    }

    @Inject(method = "loadNBTData", at = @At("RETURN"), require = 1)
    private void apeiron$load(NBTTagCompound tag, CallbackInfo ci) {
        if (tag.hasKey("ApeironPlasmaMixer", 10)) {
            NBTTagCompound settings = tag.getCompoundTag("ApeironPlasmaMixer");
            apeiron$parallel = BigValueCodec.readNBT(settings, "parallel", "parallelBig")
                .toBigInteger()
                .max(BigInteger.ZERO);
            apeiron$duration = Math.max(1, settings.getInteger("duration"));
        } else {
            apeiron$parallel = tag.hasKey("eMultiplier")
                ? BigInteger.valueOf(Math.max(0, tag.getInteger("eMultiplier")))
                : BigInteger.valueOf(Integer.MAX_VALUE);
            apeiron$duration = 1;
        }
        if (getWirelessRecipeState().isRunning()) recordPlasmaMixerEnergy(getWirelessRecipeState().getTotalEUBig());
    }
}
