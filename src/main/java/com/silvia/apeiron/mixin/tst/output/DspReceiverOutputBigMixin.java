package com.silvia.apeiron.mixin.tst.output;

import java.math.BigInteger;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase;
import com.Nxer.TwistSpaceTechnology.system.DysonSphereProgram.logic.DSP_Values;
import com.Nxer.TwistSpaceTechnology.system.DysonSphereProgram.machines.TST_DSPReceiver;
import com.silvia.apeiron.api.machine.tst.BigTstOutputController;
import com.silvia.apeiron.common.machine.output.NativeOutputBatches;

import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;

/** Convert the full native EU accumulator before any long/double conversion or int-stack splitting. */
@Mixin(value = TST_DSPReceiver.class, remap = false)
public abstract class DspReceiverOutputBigMixin extends GTCM_MultiMachineBase<TST_DSPReceiver> {

    @Shadow
    protected long storageEU;
    @Shadow
    protected long storageEUMAX;
    @Shadow
    protected static ItemStack CRITICAL_PHOTON;

    protected DspReceiverOutputBigMixin(String name) {
        super(name);
    }

    @Inject(
        method = "checkProcessing",
        at = @At(
            value = "INVOKE",
            target = "Lcom/Nxer/TwistSpaceTechnology/system/DysonSphereProgram/machines/TST_DSPReceiver;syncDSPData()V",
            shift = At.Shift.AFTER),
        cancellable = true,
        require = 1)
    private void apeiron$collectPhotons(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (machineMode != 1 || !NativeOutputBatches.hasExactItems(this) || DSP_Values.EUPerCriticalPhoton <= 0) return;
        BigInteger radix = BigInteger.valueOf(Integer.MAX_VALUE);
        BigInteger energy = BigInteger.valueOf(storageEUMAX)
            .multiply(radix)
            .add(BigInteger.valueOf(storageEU));
        if (energy.signum() < 0) return;
        BigInteger[] photons = energy.divideAndRemainder(BigInteger.valueOf(DSP_Values.EUPerCriticalPhoton));
        ((BigTstOutputController) this).mergeItemIntoMEOutputQueueBig(CRITICAL_PHOTON, photons[0]);
        BigInteger[] remainder = photons[1].divideAndRemainder(radix);
        storageEUMAX = remainder[0].longValueExact();
        storageEU = remainder[1].longValueExact();
        mOutputItems = null;
        mMaxProgresstime = 128;
        markDirty();
        cir.setReturnValue(CheckRecipeResultRegistry.GENERATING);
    }
}
