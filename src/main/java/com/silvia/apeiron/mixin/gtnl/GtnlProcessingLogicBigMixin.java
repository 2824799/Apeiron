package com.silvia.apeiron.mixin.gtnl;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.science.gtnl.utils.recipes.GTNLOverclockCalculator;
import com.science.gtnl.utils.recipes.GTNLParallelHelper;
import com.science.gtnl.utils.recipes.GTNLProcessingLogic;
import com.silvia.apeiron.api.machine.gtnl.BigGtnlRecipeLogic;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.integration.gtnl.parallel.BigGtnlParallelHelper;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;

@Pseudo
@Mixin(targets = "com.science.gtnl.utils.recipes.GTNLProcessingLogic", remap = false)
public abstract class GtnlProcessingLogicBigMixin extends gregtech.api.logic.ProcessingLogic
    implements BigGtnlRecipeLogic {

    @Unique
    private BigGtnlParallelHelper apeiron$prepared;

    @Override
    public BigGtnlParallelHelper getGtnlPreparedRecipe() {
        return apeiron$prepared;
    }

    @Inject(method = "clear()Lcom/science/gtnl/utils/recipes/GTNLProcessingLogic;", at = @At("HEAD"), require = 1)
    private void apeiron$clear(CallbackInfoReturnable<GTNLProcessingLogic> cir) {
        apeiron$prepared = null;
    }

    @Inject(method = "process", at = @At("HEAD"), require = 1)
    private void apeiron$power(CallbackInfoReturnable<CheckRecipeResult> cir) {
        apeiron$prepared = null;
        if (!(machine instanceof MTEMultiBlockBase) || InfiniteEnergyHatches.find((MTEMultiBlockBase) machine) == null)
            return;
        GTNLProcessingLogic logic = (GTNLProcessingLogic) (Object) this;
        logic.setAvailableVoltage(InfiniteEnergyHatches.processingVoltage((MTEMultiBlockBase) machine));
        logic.setAvailableAmperage(1);
        logic.setAmperageOC(false);
    }

    @Redirect(
        method = "validateAndCalculateRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lcom/science/gtnl/utils/recipes/GTNLProcessingLogic;createParallelHelper(Lgregtech/api/util/GTRecipe;)Lcom/science/gtnl/utils/recipes/GTNLParallelHelper;"),
        require = 1)
    private GTNLParallelHelper apeiron$helper(GTNLProcessingLogic logic, GTRecipe recipe) {
        GTNLParallelHelper helper = BigGtnlParallelHelper.adapt(logic.createParallelHelper(recipe));
        if (helper instanceof BigGtnlParallelHelper) ((BigGtnlParallelHelper) helper).setDurationAdjustment(big -> {
            double ticks = logic.calculateDuration(recipe, big, big.calculator);
            for (gregtech.api.metatileentity.implementations.MTEHatchMaintenance maintenance : ((MTEMultiBlockBase) machine).mMaintenanceHatches)
                if (maintenance instanceof com.science.gtnl.api.IConfigurationMaintenance
                    && ((com.science.gtnl.api.IConfigurationMaintenance) maintenance).isConfiguration()) {
                        ticks = Math.max(
                            1,
                            ticks * ((com.science.gtnl.api.IConfigurationMaintenance) maintenance).getConfigTime()
                                / 100.0);
                        break;
                    }
            return ticks;
        });
        return helper;
    }

    @Redirect(
        method = "validateAndCalculateRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lcom/science/gtnl/utils/recipes/GTNLProcessingLogic;validateRecipe(Lgregtech/api/util/GTRecipe;)Lgregtech/api/recipe/check/CheckRecipeResult;"),
        require = 1)
    private CheckRecipeResult apeiron$validate(GTNLProcessingLogic logic, GTRecipe recipe) {
        CheckRecipeResult result = logic.validateRecipe(recipe);
        if (machine instanceof MTEMultiBlockBase && InfiniteEnergyHatches.find((MTEMultiBlockBase) machine) != null
            && ("insufficient_power".equals(result.getID()) || "insufficient_voltage".equals(result.getID())))
            return CheckRecipeResultRegistry.SUCCESSFUL;
        return result;
    }

    @Inject(
        method = "applyRecipe(Lgregtech/api/util/GTRecipe;Lcom/science/gtnl/utils/recipes/GTNLParallelHelper;Lcom/science/gtnl/utils/recipes/GTNLOverclockCalculator;Lgregtech/api/recipe/check/CheckRecipeResult;)Lgregtech/api/recipe/check/CheckRecipeResult;",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$prepare(GTRecipe recipe, GTNLParallelHelper helper, GTNLOverclockCalculator calculator,
        CheckRecipeResult result, CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (!(helper instanceof BigGtnlParallelHelper)) return;
        BigGtnlParallelHelper big = (BigGtnlParallelHelper) helper;
        CheckRecipeResult started = ((GTNLProcessingLogic) (Object) this).onRecipeStart(recipe);
        if (!started.wasSuccessful()) {
            cir.setReturnValue(started);
            return;
        }
        duration = big.getDuration();
        calculatedEut = big.getEutBig()
            .min(BigInteger.valueOf(Long.MAX_VALUE - 1))
            .longValue();
        calculatedParallels = big.getCurrentParallel();
        lastRecipe = recipe.mCanBeBuffered ? recipe : null;
        ((GTNLProcessingLogic) (Object) this).overwriteOutputItems(helper.getItemOutputs())
            .overwriteOutputFluids(helper.getFluidOutputs());
        apeiron$prepared = big;
        if (!big.isWirelessBatch()) ((BigWirelessController) machine).getWirelessRecipeState()
            .prepare(big, duration);
        cir.setReturnValue(result);
    }
}
