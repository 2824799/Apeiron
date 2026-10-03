package com.silvia.apeiron.mixin.tst.energy;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase;
import com.Nxer.TwistSpaceTechnology.system.OreProcess.machines.TST_OreProcessingFactory;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.energy.WirelessControllerEnergy;
import com.silvia.apeiron.common.machine.tst.OreFactoryWirelessRecipes;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;

/** Uses exact recipe plans instead of TST's long-based ore loop; its periodic lubricant callback stays in use. */
@Pseudo
@Mixin(targets = "com.Nxer.TwistSpaceTechnology.system.OreProcess.machines.TST_OreProcessingFactory", remap = false)
public abstract class OreFactoryWirelessEnergyMixin extends GTCM_MultiMachineBase<TST_OreProcessingFactory> {

    protected OreFactoryWirelessEnergyMixin(String name) {
        super(name);
    }

    @Inject(method = "getRecipeMap", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$recipes(CallbackInfoReturnable<RecipeMap<?>> cir) {
        if (InfiniteEnergyHatches.find(this) != null) cir.setReturnValue(OreFactoryWirelessRecipes.get());
    }

    @Inject(method = "checkProcessing", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$process(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (InfiniteEnergyHatches.find(this) != null) cir.setReturnValue(super.checkProcessing());
    }

    @Inject(method = "onRunningTick", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$debit(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!((BigWirelessController) this).getWirelessRecipeState()
            .isRunning()) return;
        if (!WirelessControllerEnergy.debitTick(this)) cir.setReturnValue(false);
    }

}
