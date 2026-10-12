package com.silvia.apeiron.mixin.gregtech.purification;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigPurificationUnit;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.output.NativeOutputBatches;
import com.silvia.apeiron.common.machine.parallel.PurificationParallels;
import com.silvia.apeiron.common.machine.parallel.PurificationWaterInputs;
import com.silvia.apeiron.compat.OutputTransactions;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitBase;

/** Native cycle, stage conditions and plant power remain in charge; water quantities use exact transactions. */
@Mixin(value = MTEPurificationUnitBase.class, remap = false)
public abstract class PurificationWaterMixin implements BigPurificationUnit {

    @Shadow
    protected GTRecipe currentRecipe;
    @Shadow
    protected int maxParallel;
    @Shadow
    protected int effectiveParallel;

    @Shadow
    private FluidStack getDegradedOutputWater() {
        throw new AssertionError();
    }

    @Unique
    private final BigMachineOutputQueue apeiron$cycleOutputs = new BigMachineOutputQueue();
    @Unique
    private BigInteger apeiron$waterOutput = BigInteger.ZERO;
    @Unique
    private boolean apeiron$recorded;
    @Unique
    private boolean apeiron$released;
    @Unique
    private boolean apeiron$starting;
    @Unique
    private boolean apeiron$boosted;
    @Unique
    private boolean apeiron$debited;
    @Unique
    private BigInteger apeiron$parallelSetting;
    @Unique
    private BigInteger apeiron$effectiveParallel = BigInteger.ONE;

    @Unique
    private MTEPurificationUnitBase<?> apeiron$unit() {
        return (MTEPurificationUnitBase<?>) (Object) this;
    }

    @Override
    public BigInteger getPurificationParallelSettingBig() {
        return apeiron$parallelSetting == null ? BigInteger.valueOf(Math.max(1, maxParallel)) : apeiron$parallelSetting;
    }

    @Override
    public void setPurificationParallelSettingBig(BigInteger value) {
        if (value.signum() < 0) throw new IllegalArgumentException("Negative purification parallel limit");
        apeiron$parallelSetting = value;
        maxParallel = value.signum() == 0 ? Integer.MAX_VALUE
            : value.min(BigInteger.valueOf(Integer.MAX_VALUE))
                .intValue();
    }

    @Override
    public BigInteger getPurificationParallelsBig() {
        return apeiron$effectiveParallel;
    }

    @Override
    public BigInteger getPurificationPowerUsageBig() {
        return BigInteger.valueOf(apeiron$unit().getBasePowerUsage())
            .multiply(apeiron$effectiveParallel);
    }

    @Inject(method = "setMaxParallel", at = @At("HEAD"), require = 1)
    private void apeiron$nativeSetting(int value, CallbackInfo ci) {
        // An int sync of the compatibility projection must not replace a saved exact setting.
        if (value != maxParallel) apeiron$parallelSetting = BigInteger.valueOf(Math.max(1, value));
    }

    @Inject(method = "doPurificationRecipeCheck", at = @At("HEAD"), require = 1)
    private void apeiron$beginCheck(CallbackInfoReturnable<Boolean> cir) {
        apeiron$effectiveParallel = BigInteger.ONE;
    }

    @Inject(method = "getActualPowerUsage", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$power(CallbackInfoReturnable<Long> cir) {
        if (PurificationParallels.source(apeiron$unit()) != null) cir.setReturnValue(
            getPurificationPowerUsageBig().min(BigInteger.valueOf(Long.MAX_VALUE))
                .longValue());
    }

    @Override
    public BigInteger getPurificationWaterOutputBig() {
        return apeiron$waterOutput;
    }

    @Override
    public boolean isPurificationOutputPendingBig() {
        return apeiron$recorded && !apeiron$released;
    }

    @Override
    public boolean copyPurificationRecipeFluidsBig(BigMachineOutputQueue target) {
        if (!apeiron$recorded) return false;
        for (IAEStack<?> output : apeiron$cycleOutputs.snapshotOutputsUnsorted())
            target.addFluid(((IAEFluidStack) output).getFluidStack(), BigAEStackValues.get(output));
        return true;
    }

    @Inject(method = "checkProcessing", at = @At("RETURN"), cancellable = true, require = 1)
    private void apeiron$parallel(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (!cir.getReturnValue()
            .wasSuccessful() || currentRecipe == null
            || !PurificationWaterInputs.enabled(apeiron$unit())
            || PurificationWaterInputs.usesSplitCycleOutputs(apeiron$unit())) return;
        apeiron$updateParallel();
        if (effectiveParallel == 0) cir.setReturnValue(CheckRecipeResultRegistry.NO_RECIPE);
    }

    @Unique
    private void apeiron$updateParallel() {
        apeiron$effectiveParallel = PurificationWaterInputs.parallel(apeiron$unit(), currentRecipe.mFluidInputs[0]);
        effectiveParallel = apeiron$effectiveParallel.min(BigInteger.valueOf(Integer.MAX_VALUE))
            .intValue();
    }

    @Inject(method = "isWaterBoosted", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$boost(GTRecipe recipe, CallbackInfoReturnable<Boolean> cir) {
        if (!PurificationWaterInputs.enabled(apeiron$unit())
            || PurificationWaterInputs.usesSplitCycleOutputs(apeiron$unit())) return;
        if (apeiron$starting) {
            cir.setReturnValue(apeiron$boosted);
            return;
        }
        List<IAEFluidStack> required = PurificationWaterInputs.recipeInputs(recipe, apeiron$effectiveParallel);
        required.add(PurificationWaterInputs.boost(recipe, apeiron$effectiveParallel));
        cir.setReturnValue(PurificationWaterInputs.consume(apeiron$unit(), required, true));
    }

    @Inject(
        method = "startCycle",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/common/tileentities/machines/multi/purification/MTEPurificationUnitBase;calculateBoostedSuccessChance()F"),
        require = 1)
    private void apeiron$prepare(int cycleTime, int progressTime, CallbackInfo ci) {
        apeiron$starting = PurificationWaterInputs.enabled(apeiron$unit());
        apeiron$debited = false;
        if (!apeiron$starting) return;
        apeiron$updateParallel();
        List<IAEFluidStack> required = PurificationWaterInputs.recipeInputs(currentRecipe, apeiron$effectiveParallel);
        required.add(PurificationWaterInputs.boost(currentRecipe, apeiron$effectiveParallel));
        apeiron$boosted = effectiveParallel > 0 && PurificationWaterInputs.consume(apeiron$unit(), required, true);
    }

    @Inject(
        method = "startCycle",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/common/tileentities/machines/multi/purification/MTEPurificationUnitBase;calculateBoostedSuccessChance()F",
            shift = At.Shift.AFTER),
        cancellable = true,
        require = 1)
    private void apeiron$debit(int cycleTime, int progressTime, CallbackInfo ci) {
        if (!apeiron$starting) return;
        List<IAEFluidStack> required = apeiron$unit().getController()
            .debugModeOn() ? new ArrayList<>()
                : PurificationWaterInputs.recipeInputs(currentRecipe, apeiron$effectiveParallel);
        if (apeiron$boosted) required.add(PurificationWaterInputs.boost(currentRecipe, apeiron$effectiveParallel));
        if (effectiveParallel <= 0 || !PurificationWaterInputs.consume(apeiron$unit(), required, false)) {
            apeiron$starting = false;
            apeiron$unit().mMaxProgresstime = 0;
            effectiveParallel = 0;
            apeiron$effectiveParallel = BigInteger.ZERO;
            apeiron$unit().endRecipeProcessing();
            ci.cancel();
            return;
        }
        apeiron$debited = true;
    }

    @Redirect(
        method = "startCycle",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/common/tileentities/machines/multi/purification/MTEPurificationUnitBase;depleteInput(Lnet/minecraftforge/fluids/FluidStack;)Z"),
        require = 1)
    private boolean apeiron$boostDebit(MTEPurificationUnitBase<?> unit, FluidStack boost) {
        return apeiron$debited || unit.depleteInput(boost);
    }

    @Inject(method = "depleteRecipeInputs", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$recipeDebit(CallbackInfo ci) {
        if (!PurificationWaterInputs.enabled(apeiron$unit())) return;
        if (!apeiron$debited) {
            PurificationWaterInputs.consume(
                apeiron$unit(),
                PurificationWaterInputs.recipeInputs(currentRecipe, apeiron$effectiveParallel),
                false);
        }
        ci.cancel();
    }

    @WrapMethod(method = "startCycle")
    private void apeiron$outputs(int cycleTime, int progressTime, Operation<Void> original) {
        apeiron$cycleOutputs.load(new NBTTagCompound());
        apeiron$waterOutput = BigInteger.ZERO;
        apeiron$recorded = apeiron$released = false;
        try {
            original.call(cycleTime, progressTime);
        } finally {
            apeiron$starting = false;
            apeiron$debited = false;
        }
        MTEPurificationUnitBase<?> unit = apeiron$unit();
        if (unit.mMaxProgresstime <= 0 || unit.mOutputFluids == null || currentRecipe == null) return;
        if (!PurificationWaterInputs.enabled(unit) && !NativeOutputBatches.hasExactFluids(unit)) return;
        // Wrap the completed method: optional mods can cancel startCycle before a RETURN injection runs.
        if (PurificationWaterInputs.usesSplitCycleOutputs(unit)) {
            // Keep GTNL's actual generated outputs, including any optional output modifiers.
            for (FluidStack output : unit.mOutputFluids) {
                if (output == null || output.amount <= 0) continue;
                BigInteger amount = BigInteger.valueOf(output.amount);
                apeiron$cycleOutputs.addFluid(output, amount);
                if (output.isFluidEqual(currentRecipe.mFluidOutputs[0]))
                    apeiron$waterOutput = apeiron$waterOutput.add(amount);
            }
        } else {
            BigInteger parallel = PurificationWaterInputs.enabled(unit) ? apeiron$effectiveParallel
                : BigInteger.valueOf(effectiveParallel);
            for (FluidStack output : currentRecipe.mFluidOutputs) apeiron$cycleOutputs.addFluid(
                output,
                BigInteger.valueOf(output.amount)
                    .multiply(parallel));
            apeiron$waterOutput = BigInteger.valueOf(currentRecipe.mFluidOutputs[0].amount)
                .multiply(parallel);
        }
        apeiron$recorded = true;
    }

    @Inject(method = "addRecipeOutputs", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$complete(CallbackInfo ci) {
        if (!apeiron$recorded) return;
        if (apeiron$released) {
            ci.cancel();
            return;
        }
        apeiron$released = true;
        MTEPurificationUnitBase<?> unit = apeiron$unit();
        apeiron$cycleOutputs.moveTo(
            ((BigWirelessController) unit).getWirelessRecipeState()
                .pending());
        if (unit.mOutputItems != null) unit.addItemOutputs(unit.mOutputItems);
        apeiron$flush(unit);
        ci.cancel();
    }

    @Inject(method = "onRecipeFail", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$degraded(CallbackInfo ci) {
        if (!apeiron$recorded) return;
        if (apeiron$released) {
            ci.cancel();
            return;
        }
        apeiron$released = true;
        MTEPurificationUnitBase<?> unit = apeiron$unit();
        FluidStack output = unit.mOutputFluids == null || unit.mOutputFluids.length == 0 ? null
            : getDegradedOutputWater();
        if (output != null) {
            // Native failed-cycle water may be voided when no output space is available.
            BigMachineOutputQueue degraded = new BigMachineOutputQueue();
            degraded.addFluid(output, apeiron$waterOutput);
            degraded.flush(unit.getOutputBusses(), OutputTransactions.hatches(unit), false, false);
        }
        ci.cancel();
    }

    @Unique
    private static void apeiron$flush(MTEMultiBlockBase unit) {
        ((BigWirelessController) unit).getWirelessRecipeState()
            .pending()
            .flush(
                unit.getOutputBusses(),
                OutputTransactions.hatches(unit),
                unit.protectsExcessItem(),
                unit.protectsExcessFluid());
        unit.markDirty();
    }

    @Inject(method = "endCycle", at = @At("RETURN"), require = 1)
    private void apeiron$reset(CallbackInfo ci) {
        apeiron$cycleOutputs.load(new NBTTagCompound());
        apeiron$waterOutput = BigInteger.ZERO;
        apeiron$debited = apeiron$starting = false;
        apeiron$recorded = apeiron$released = false;
        apeiron$effectiveParallel = BigInteger.ONE;
    }

    @Inject(method = "saveNBTData", at = @At("RETURN"), require = 1)
    private void apeiron$save(NBTTagCompound tag, CallbackInfo ci) {
        NBTTagCompound parallels = new NBTTagCompound();
        parallels.setString("setting", getPurificationParallelSettingBig().toString());
        parallels.setString("effective", apeiron$effectiveParallel.toString());
        tag.setTag("ApeironPurificationParallel", parallels);
        if (!apeiron$recorded) {
            tag.removeTag("ApeironPurificationCycle");
            return;
        }
        NBTTagCompound saved = new NBTTagCompound();
        apeiron$cycleOutputs.save(saved);
        saved.setString("water", apeiron$waterOutput.toString());
        saved.setBoolean("released", apeiron$released);
        tag.setTag("ApeironPurificationCycle", saved);
    }

    @Inject(method = "loadNBTData", at = @At("RETURN"), require = 1)
    private void apeiron$load(NBTTagCompound tag, CallbackInfo ci) {
        NBTTagCompound parallels = tag.getCompoundTag("ApeironPurificationParallel");
        apeiron$parallelSetting = null;
        if (parallels.hasKey("setting"))
            setPurificationParallelSettingBig(new BigInteger(parallels.getString("setting")));
        apeiron$effectiveParallel = parallels.hasKey("effective") ? new BigInteger(parallels.getString("effective"))
            : BigInteger.valueOf(effectiveParallel);
        NBTTagCompound saved = tag.getCompoundTag("ApeironPurificationCycle");
        apeiron$cycleOutputs.load(saved);
        apeiron$waterOutput = saved.hasKey("water") ? new BigInteger(saved.getString("water")) : BigInteger.ZERO;
        apeiron$recorded = saved.hasKey("water");
        apeiron$released = saved.hasKey("released") ? saved.getBoolean("released")
            : apeiron$recorded && apeiron$cycleOutputs.isEmpty();
    }
}
