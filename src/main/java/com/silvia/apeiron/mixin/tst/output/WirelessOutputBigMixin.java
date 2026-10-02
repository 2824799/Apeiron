package com.silvia.apeiron.mixin.tst.output;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase.FluidStackLong;
import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase.ItemStackLong;
import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.processingLogics.GTCM_ProcessingLogic;
import com.silvia.apeiron.api.machine.tst.BigTstOutputController;
import com.silvia.apeiron.common.machine.tst.output.BigTstOutputLists;
import com.silvia.apeiron.mixin.gregtech.output.MultiBlockProcessingAccessor;

import gregtech.api.logic.ProcessingLogic;

@Pseudo
@Mixin(
    targets = "com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.WirelessEnergyMultiMachineBase",
    remap = false)
public abstract class WirelessOutputBigMixin {

    @Inject(method = "mergeWirelessOutputsIntoMEQueue", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$accumulate(CallbackInfo ci) {
        final ProcessingLogic processingLogic = ((MultiBlockProcessingAccessor) this).apeiron$getProcessingLogic();
        if (!(processingLogic instanceof GTCM_ProcessingLogic)) return;
        final GTCM_ProcessingLogic logic = (GTCM_ProcessingLogic) processingLogic;
        if (!logic.hasLongOutputs()) return;
        final BigTstOutputController machine = (BigTstOutputController) this;
        for (ItemStackLong entry : logic.getLongItemOutputs())
            machine.mergeItemIntoMEOutputQueueBig(entry.itemStack(), BigTstOutputLists.amount(entry));
        for (FluidStackLong entry : logic.getLongFluidOutputs())
            machine.mergeFluidIntoMEOutputQueueBig(entry.fluidStack(), BigTstOutputLists.amount(entry));
        ci.cancel();
    }
}
