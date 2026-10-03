package com.silvia.apeiron.mixin.gregtech.energy;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
        if (machine instanceof MTEMultiBlockBase && InfiniteEnergyHatches.isUltimate((MTEMultiBlockBase) machine))
            return OverclockCalculator.ofNoOverclock(recipe);
        OverclockCalculator calculator = ((ProcessingLogicAccessor) logic).apeiron$nativeCalculator(recipe);
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
        if (!BigRecipeParallelHelper.supports(recipe) || !NativeParallelPolicy.supports(nativeHelper))
            return nativeHelper;
        return new BigRecipeParallelHelper(
            controller,
            hatch,
            ((BigWirelessController) machine).getWirelessRecipeState()).setRecipe(recipe)
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
