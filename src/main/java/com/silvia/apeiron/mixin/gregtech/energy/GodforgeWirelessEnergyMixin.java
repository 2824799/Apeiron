package com.silvia.apeiron.mixin.gregtech.energy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.api.machine.parallel.BigWirelessRecipeListener;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import tectech.thing.metaTileEntity.multi.godforge.MTEBaseModule;
import tectech.thing.metaTileEntity.multi.godforge.MTEExoticModule;

@Mixin(value = MTEBaseModule.class, remap = false)
public abstract class GodforgeWirelessEnergyMixin implements BigWirelessRecipeListener, BigWirelessController {

    @Unique
    private long apeiron$previousRecipeTally;

    @Override
    public void beforeWirelessRecipeStart() {
        apeiron$previousRecipeTally = ((MTEBaseModule) (Object) this).getRecipeTally();
    }

    @Inject(method = "getActualParallel", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$parallel(CallbackInfoReturnable<Integer> cir) {
        MTEMultiBlockBase machine = (MTEMultiBlockBase) (Object) this;
        if (InfiniteEnergyHatches.find(machine) == null) return;
        // Generate one exotic recipe. The exact planner applies the hatch's absolute
        // parallel setting; multiplying by the main controller here would count it twice.
        if (machine instanceof MTEExoticModule) {
            cir.setReturnValue(1);
            return;
        }
        java.math.BigInteger cap = ((BigWirelessController) this).getWirelessRecipeState()
            .getParallelSettingBig();
        cir.setReturnValue(
            cap.signum() == 0 ? Integer.MAX_VALUE
                : cap.min(java.math.BigInteger.valueOf(Integer.MAX_VALUE))
                    .intValue());
    }

    @Override
    public void onWirelessRecipeStarted(java.math.BigInteger parallels, java.math.BigInteger totalEnergy) {
        MTEBaseModule module = (MTEBaseModule) (Object) this;
        module.addToPowerTally(totalEnergy);
        // Native startup counts an int compatibility view and can overflow an already saturated long.
        boolean furnace = module instanceof tectech.thing.metaTileEntity.multi.godforge.MTESmeltingModule
            && module.getRecipeMap() == gregtech.api.recipe.RecipeMaps.furnaceRecipes;
        if (furnace) return;
        module.setRecipeTally(
            java.math.BigInteger.valueOf(apeiron$previousRecipeTally)
                .add(parallels)
                .min(java.math.BigInteger.valueOf(Long.MAX_VALUE))
                .longValueExact());
    }

    @Inject(
        method = { "getProcessingVoltage", "getSafeProcessingVoltage", "getMaxInputVoltage" },
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$voltage(CallbackInfoReturnable<Long> cir) {
        MTEMultiBlockBase machine = (MTEMultiBlockBase) (Object) this;
        if (InfiniteEnergyHatches.find(machine) != null)
            cir.setReturnValue(InfiniteEnergyHatches.processingVoltage(machine));
    }
}
