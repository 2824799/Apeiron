package com.silvia.apeiron.mixin.gtnl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.science.gtnl.common.machine.multiMachineBase.WirelessEnergyMultiMachineBase;
import com.silvia.apeiron.api.machine.gtnl.BigGtnlWirelessMachine;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;

import gregtech.api.recipe.check.CheckRecipeResult;

/** Subclass startup checks remain native; only the enclosing batch ledger and ultimate mode are shared. */
@Pseudo
@Mixin(
    targets = { "com.science.gtnl.common.machine.multiMachineBase.WirelessEnergyMultiMachineBase",
        "com.science.gtnl.common.machine.multiblock.wireless.HighwayToHell",
        "com.science.gtnl.common.machine.multiblock.wireless.TransliminalOasis",
        "com.science.gtnl.common.machine.multiblock.PCBFactory",
        "com.science.gtnl.common.machine.multiblock.wireless.NineIndustrialMultiMachine",
        "com.science.gtnl.common.machine.multiblock.wireless.MantleCrusher",
        "com.science.gtnl.common.machine.multiblock.wireless.CrackerHub",
        "com.science.gtnl.common.machine.multiblock.wireless.SmeltingMixingFurnace" },
    remap = false)
public abstract class GtnlWirelessBatchMixin {

    @Inject(method = "checkProcessing", at = @At("HEAD"), require = 1)
    private void apeiron$begin(CallbackInfoReturnable<CheckRecipeResult> cir) {
        WirelessEnergyMultiMachineBase<?> machine = (WirelessEnergyMultiMachineBase<?>) (Object) this;
        if (InfiniteEnergyHatches.isUltimate(machine)) machine.wirelessMode = true;
        if (machine.wirelessMode) ((BigGtnlWirelessMachine) this).getGtnlRecipeBatch()
            .enter(machine);
    }

    @Inject(method = "checkProcessing", at = @At("RETURN"), require = 1)
    private void apeiron$finish(CallbackInfoReturnable<CheckRecipeResult> cir) {
        WirelessEnergyMultiMachineBase<?> machine = (WirelessEnergyMultiMachineBase<?>) (Object) this;
        ((BigGtnlWirelessMachine) this).getGtnlRecipeBatch()
            .leave(machine);
    }
}
