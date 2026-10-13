package com.silvia.apeiron.mixin.messtech;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.api.machine.parallel.BigSpaceElevatorModule;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.api.machine.parallel.ParallelLimit;
import com.silvia.apeiron.common.integration.messtech.MessRecipeController;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;

@Pseudo
@Mixin(targets = "com.MessTech.common.machine.module.SpaceModuleInfinityBase", remap = false)
public abstract class MessModuleMixin
    implements BigSpaceElevatorModule, com.silvia.apeiron.common.integration.messtech.MessModuleController {

    @Inject(method = "supportsPowerPanel", at = @At("RETURN"), cancellable = true, require = 1)
    private void apeiron$controls(CallbackInfoReturnable<Boolean> cir) {
        if (InfiniteEnergyHatches.find((MTEMultiBlockBase) (Object) this) != null) cir.setReturnValue(true);
    }

    @Override
    public MTEMultiBlockBase getEnergyParent() {
        return (MTEMultiBlockBase) com.silvia.apeiron.common.integration.messtech.MessTechAccess
            .get(this, "parentElevator");
    }

    @Override
    public ParallelLimit getModuleParallelLimitBig() {
        MTEMultiBlockBase machine = (MTEMultiBlockBase) (Object) this;
        if (InfiniteEnergyHatches.find(machine) == null) return ParallelLimit.bounded(machine.getTrueParallel());
        ParallelLimit limit = ((BigWirelessController) this).getWirelessRecipeState()
            .getLimit();
        if (limit.isUnlimited()) return limit;
        int slots = Math.max(
            1,
            ((Number) com.silvia.apeiron.common.integration.messtech.MessTechAccess.call(this, "getBatchTaskCount"))
                .intValue());
        if (machine.getClass()
            .getSimpleName()
            .equals("SpaceModuleAssemblerInfinity"))
            slots = ((Number) com.silvia.apeiron.common.integration.messtech.MessTechAccess
                .get(this, "MAX_CROSS_RECIPE_CYCLES")).intValue();
        return ParallelLimit.bounded(
            limit.getBound()
                .get()
                .multiply(java.math.BigInteger.valueOf(slots)));
    }

    @Inject(method = "checkProcessingWirelessLoop", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$begin(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (InfiniteEnergyHatches.find((MTEMultiBlockBase) (Object) this) == null) return;
        if (((BigWirelessController) this).getWirelessRecipeState()
            .isRunning()) cir.setReturnValue(CheckRecipeResultRegistry.NO_RECIPE);
        else((MessRecipeController) this).getMessRecipeBatch()
            .begin();
    }

    @Redirect(
        method = "checkProcessingWirelessLoop",
        at = @At(
            value = "INVOKE",
            target = "Lcom/MessTech/common/machine/module/SpaceModuleInfinityBase;wirelessModeProcessOnce()Lgregtech/api/recipe/check/CheckRecipeResult;"),
        require = 1)
    private CheckRecipeResult apeiron$step(@Coerce Object machine) {
        return ((MessRecipeController) machine).getMessRecipeBatch()
            .step((MTEMultiBlockBase) machine);
    }

    @Inject(method = "checkProcessingWirelessLoop", at = @At("RETURN"), require = 1)
    private void apeiron$finish(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (InfiniteEnergyHatches.find((MTEMultiBlockBase) (Object) this) != null)
            ((MessRecipeController) this).getMessRecipeBatch()
                .finish((MTEMultiBlockBase) (Object) this);
    }

    @Inject(method = "onRunningTick", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$exactTick(net.minecraft.item.ItemStack slot, CallbackInfoReturnable<Boolean> cir) {
        com.silvia.apeiron.common.machine.parallel.WirelessRecipeState state = ((BigWirelessController) this)
            .getWirelessRecipeState();
        if (state.isRunning() && !state.usesNativeEnergy()) cir.setReturnValue(
            com.silvia.apeiron.common.machine.energy.WirelessControllerEnergy
                .debitTick((MTEMultiBlockBase) (Object) this));
    }
}
