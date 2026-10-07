package com.silvia.apeiron.mixin.gtnl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.api.machine.gtnl.BigGtnlWirelessMachine;
import com.silvia.apeiron.common.integration.gtnl.parallel.GtnlRecipeBatch;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;

import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

@Pseudo
@Mixin(targets = "com.science.gtnl.common.machine.multiMachineBase.WirelessEnergyMultiMachineBase", remap = false)
public abstract class GtnlWirelessMachineMixin implements BigGtnlWirelessMachine {

    @Unique
    private final GtnlRecipeBatch apeiron$batch = new GtnlRecipeBatch();

    @Override
    public GtnlRecipeBatch getGtnlRecipeBatch() {
        return apeiron$batch;
    }

    @Inject(method = "loadNBTData", at = @At("RETURN"), require = 1)
    private void apeiron$restoreCost(net.minecraft.nbt.NBTTagCompound tag, CallbackInfo ci) {
        com.silvia.apeiron.common.machine.parallel.WirelessRecipeState state = ((com.silvia.apeiron.api.machine.parallel.BigWirelessController) this)
            .getWirelessRecipeState();
        if (!state.isRunning()) return;
        com.science.gtnl.common.machine.multiMachineBase.WirelessEnergyMultiMachineBase<?> machine = (com.science.gtnl.common.machine.multiMachineBase.WirelessEnergyMultiMachineBase<?>) (Object) this;
        machine.costingEU = state.getTotalEUBig();
        machine.costingEUText = com.silvia.apeiron.math.BigNumberFormatter.formatCompact(machine.costingEU);
    }

    @Inject(method = "setWirelessMode", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$forceWireless(boolean enabled, CallbackInfo ci) {
        MTEMultiBlockBase machine = (MTEMultiBlockBase) (Object) this;
        if (!InfiniteEnergyHatches.isUltimate(machine)) return;
        ((com.science.gtnl.common.machine.multiMachineBase.WirelessEnergyMultiMachineBase<?>) machine).wirelessMode = true;
        ci.cancel();
    }

    @Inject(method = "setProcessingLogicPower", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$power(ProcessingLogic logic, CallbackInfo ci) {
        MTEMultiBlockBase machine = (MTEMultiBlockBase) (Object) this;
        if (InfiniteEnergyHatches.find(machine) == null) return;
        logic.setAvailableVoltage(InfiniteEnergyHatches.processingVoltage(machine))
            .setAvailableAmperage(1)
            .setAmperageOC(false);
        ci.cancel();
    }
}
