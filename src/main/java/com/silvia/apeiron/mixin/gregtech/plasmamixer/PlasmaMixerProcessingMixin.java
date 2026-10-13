package com.silvia.apeiron.mixin.gregtech.plasmamixer;

import java.math.BigInteger;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.api.machine.parallel.BigPlasmaMixerController;
import com.silvia.apeiron.common.machine.parallel.BigPrepaidParallelHelper;
import com.silvia.apeiron.common.machine.parallel.BigRecipeParallelHelper;
import com.silvia.apeiron.common.machine.parallel.NativeParallelPolicy;

import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeConstants;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;

@Pseudo
@Mixin(targets = "gregtech.common.tileentities.machines.multi.MTETranscendentPlasmaMixer$1", remap = false)
public abstract class PlasmaMixerProcessingMixin extends ProcessingLogic {

    @Inject(method = "validateRecipe", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$validation(GTRecipe recipe, CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (machine instanceof BigPlasmaMixerController && BigRecipeParallelHelper.supports(recipe))
            cir.setReturnValue(CheckRecipeResultRegistry.SUCCESSFUL);
    }

    @Override
    protected ParallelHelper createParallelHelper(GTRecipe recipe) {
        ParallelHelper nativeHelper = super.createParallelHelper(recipe);
        if (!(machine instanceof BigPlasmaMixerController) || !BigRecipeParallelHelper.supports(recipe)
            || !NativeParallelPolicy.supports(nativeHelper)) return nativeHelper;
        BigPlasmaMixerController controller = (BigPlasmaMixerController) machine;
        // Multiply each factor as an exact integer before combining them, including the native metadata surcharge.
        BigInteger cost = BigInteger.valueOf(recipe.mEUt)
            .multiply(BigInteger.valueOf(recipe.mDuration))
            .multiply(BigInteger.valueOf(recipe.getMetadataOrDefault(GTRecipeConstants.EU_MULTIPLIER, 10)));
        return new BigPrepaidParallelHelper(
            (MTEMultiBlockBase) machine,
            controller.getPlasmaMixerOwner(),
            controller.getParallelLimitBig(),
            cost,
            nativeHelper).setRecipe(recipe)
                .setItemInputs(inputItems)
                .setFluidInputs(inputFluids)
                .setMachine(machine, protectItems, protectFluids)
                .setOutputCalculation(true)
                .setConsumption(true);
    }

    @Override
    protected CheckRecipeResult applyRecipe(GTRecipe recipe, ParallelHelper helper, OverclockCalculator calculator,
        CheckRecipeResult previous) {
        if (!(helper instanceof BigPrepaidParallelHelper))
            return super.applyRecipe(recipe, helper, calculator, previous);
        BigPlasmaMixerController controller = (BigPlasmaMixerController) machine;
        duration = controller.getPlasmaMixerDuration();
        BigPrepaidParallelHelper big = (BigPrepaidParallelHelper) helper;
        CheckRecipeResult started = big.start(duration);
        if (!started.wasSuccessful()) return started;
        controller.recordPlasmaMixerEnergy(big.getTotalEnergyBig());
        calculatedEut = 0;
        calculatedParallels = big.getParallelsBig()
            .min(BigInteger.valueOf(Integer.MAX_VALUE))
            .intValue();
        lastRecipe = recipe.mCanBeBuffered ? recipe : null;
        overwriteOutputItems(new ItemStack[0]).overwriteOutputFluids(new FluidStack[0]);
        return previous;
    }
}
