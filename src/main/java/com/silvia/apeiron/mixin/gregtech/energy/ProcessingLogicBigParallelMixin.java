package com.silvia.apeiron.mixin.gregtech.energy;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.api.machine.parallel.BigRecipeEnergyProvider;
import com.silvia.apeiron.api.machine.parallel.BigRecipeInputProvider;
import com.silvia.apeiron.api.machine.parallel.BigRecipeOutputProvider;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.parallel.BigRecipeParallelHelper;
import com.silvia.apeiron.common.machine.parallel.NativeParallelPolicy;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;

import gregtech.api.interfaces.tileentity.IVoidable;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;

/** Reuses native recipe lookup, validation, overclocking and startup hooks around an exact parallel transaction. */
@Mixin(value = ProcessingLogic.class, remap = false)
public abstract class ProcessingLogicBigParallelMixin {

    @Shadow
    protected IVoidable machine;
    @Shadow
    protected ItemStack[] inputItems;
    @Shadow
    protected FluidStack[] inputFluids;
    @Shadow
    protected boolean protectItems;
    @Shadow
    protected boolean protectFluids;
    @Shadow
    protected int duration;
    @Shadow
    protected int calculatedParallels;
    @Shadow
    protected long calculatedEut;
    @Shadow
    protected GTRecipe lastRecipe;

    @Inject(method = "process", at = @At("HEAD"), require = 1)
    private void apeiron$power(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (machine instanceof MTEMultiBlockBase && InfiniteEnergyHatches.find((MTEMultiBlockBase) machine) != null) {
            ProcessingLogic logic = (ProcessingLogic) (Object) this;
            logic.setAvailableVoltage(InfiniteEnergyHatches.processingVoltage((MTEMultiBlockBase) machine));
            logic.setAvailableAmperage(1);
            logic.setAmperageOC(false);
        }
    }

    @Redirect(
        method = "validateAndCalculateRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/logic/ProcessingLogic;createOverclockCalculator(Lgregtech/api/util/GTRecipe;)Lgregtech/api/util/OverclockCalculator;"),
        require = 1)
    private OverclockCalculator apeiron$calculator(ProcessingLogic logic, GTRecipe recipe) {
        OverclockCalculator calculator = ((ProcessingLogicAccessor) logic).apeiron$nativeCalculator(recipe);
        if (machine instanceof MTEMultiBlockBase && InfiniteEnergyHatches.isUltimate((MTEMultiBlockBase) machine))
            calculator.setNoOverclock(true);
        if (machine instanceof MTEMultiBlockBase && InfiniteEnergyHatches.find((MTEMultiBlockBase) machine) != null)
            calculator.setEUt(InfiniteEnergyHatches.processingVoltage((MTEMultiBlockBase) machine));
        return calculator;
    }

    @Inject(method = "clear", at = @At("HEAD"), require = 1)
    private void apeiron$clearPrepared(CallbackInfoReturnable<ProcessingLogic> cir) {
        if (machine instanceof BigWirelessController) ((BigWirelessController) machine).getWirelessRecipeState()
            .discardPreparedRecipe();
    }

    @Redirect(
        method = "validateAndCalculateRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/logic/ProcessingLogic;createParallelHelper(Lgregtech/api/util/GTRecipe;)Lgregtech/api/util/ParallelHelper;"),
        require = 1)
    private ParallelHelper apeiron$exactHelper(ProcessingLogic logic, GTRecipe recipe) {
        ParallelHelper nativeHelper = ((ProcessingLogicAccessor) logic).apeiron$nativeHelper(recipe);
        if (!(machine instanceof MTEMultiBlockBase) || !(machine instanceof BigWirelessController)) return nativeHelper;
        MTEMultiBlockBase controller = (MTEMultiBlockBase) machine;
        MTEInfiniteEnergyHatch hatch = InfiniteEnergyHatches.find(controller);
        if (hatch == null || InfiniteEnergyHatches.isNativeWirelessController(controller)) return nativeHelper;
        BigRecipeOutputProvider provider = this instanceof BigRecipeOutputProvider
            && ((BigRecipeOutputProvider) this).hasExactRecipeOutputs() ? (BigRecipeOutputProvider) this : null;
        BigRecipeInputProvider inputProvider = this instanceof BigRecipeInputProvider
            && ((BigRecipeInputProvider) this).hasExactRecipeInputs() ? (BigRecipeInputProvider) this : null;
        GTRecipe inputRecipe = inputProvider == null ? recipe : inputProvider.getExactInputRecipe(recipe);
        if (inputRecipe == null || !BigRecipeParallelHelper.supportsInputs(inputRecipe)) return nativeHelper;
        if (!(provider == null ? BigRecipeParallelHelper.supports(recipe)
            : BigRecipeParallelHelper.supportsInputs(recipe))
            || !NativeParallelPolicy.supports(nativeHelper, provider != null, inputProvider != null))
            return nativeHelper;
        return new BigRecipeParallelHelper(
            controller,
            hatch,
            ((BigWirelessController) machine).getWirelessRecipeState()).preserveNativeModifiers(nativeHelper)
                .setExactInputRecipe(inputRecipe)
                .setEnergyMultiplier(
                    this instanceof BigRecipeEnergyProvider
                        ? ((BigRecipeEnergyProvider) this).getRecipeEnergyMultiplier(recipe)
                        : java.math.BigInteger.ONE)
                .setExactOutputCalculator(
                    provider == null ? null : count -> provider.calculateRecipeOutputsBig(recipe, count))
                .setRecipe(recipe)
                .setItemInputs(inputItems)
                .setFluidInputs(inputFluids)
                .setMachine(machine, protectItems, protectFluids)
                .setOutputCalculation(true)
                .setConsumption(true);
    }

    @Inject(method = "applyRecipe", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$prepare(GTRecipe recipe, ParallelHelper helper, OverclockCalculator calculator,
        CheckRecipeResult previous, CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (helper instanceof BigRecipeParallelHelper) {
            BigRecipeParallelHelper big = (BigRecipeParallelHelper) helper;
            double nativeDuration = ((ProcessingLogicAccessor) this).apeiron$duration(recipe, helper, calculator);
            if (!InfiniteEnergyHatches.isUltimate((MTEMultiBlockBase) machine) && nativeDuration >= Integer.MAX_VALUE) {
                cir.setReturnValue(gregtech.api.recipe.check.CheckRecipeResultRegistry.DURATION_OVERFLOW);
                return;
            }
            duration = big.recipeDuration((int) nativeDuration);
            if (!big.plan(duration)) {
                cir.setReturnValue(big.getResult());
                return;
            }
            calculatedEut = 0;
            lastRecipe = recipe.mCanBeBuffered ? recipe : null;
            calculatedParallels = big.getParallelsBig()
                .min(java.math.BigInteger.valueOf(Integer.MAX_VALUE))
                .intValue();
            if (machine instanceof com.silvia.apeiron.api.machine.parallel.BigWirelessRecipeListener)
                ((com.silvia.apeiron.api.machine.parallel.BigWirelessRecipeListener) machine)
                    .beforeWirelessRecipeStart();
            CheckRecipeResult started = ((ProcessingLogicAccessor) this).apeiron$onStart(recipe);
            if (!started.wasSuccessful()) {
                cir.setReturnValue(started);
                return;
            }
            ((ProcessingLogic) (Object) this).overwriteOutputItems(new ItemStack[0])
                .overwriteOutputFluids(new FluidStack[0]);
            WirelessRecipeState state = ((BigWirelessController) machine).getWirelessRecipeState();
            state.prepare(big, duration);
            calculatedParallels = ((BigRecipeParallelHelper) helper).getParallelsBig()
                .min(java.math.BigInteger.valueOf(Integer.MAX_VALUE))
                .intValue();
            cir.setReturnValue(previous);
        }
    }
}
