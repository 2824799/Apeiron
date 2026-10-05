package com.silvia.apeiron.mixin.tst.output;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase.FluidStackLong;
import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase.ItemStackLong;
import com.silvia.apeiron.api.machine.tst.BigTstOutputController;
import com.silvia.apeiron.common.machine.tst.output.BigTstOutputLists;

/** RC1 installs each recipe's long outputs by replacing both queues. */
@Pseudo
@Mixin(
    targets = "com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase",
    remap = false)
public abstract class LegacyTstOutputQueueMixin {

    @Shadow
    @Final
    protected List<ItemStackLong> meOutputQueue;
    @Shadow
    @Final
    protected List<FluidStackLong> meFluidOutputQueue;

    @Inject(
        method = "replaceMEOutputQueues(Ljava/util/List;Ljava/util/List;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$replace(List<ItemStackLong> items, List<FluidStackLong> fluids, CallbackInfo ci) {
        meOutputQueue.clear();
        meFluidOutputQueue.clear();
        BigTstOutputController controller = (BigTstOutputController) this;
        for (ItemStackLong output : items)
            controller.mergeItemIntoMEOutputQueueBig(output.itemStack(), BigTstOutputLists.amount(output));
        for (FluidStackLong output : fluids)
            controller.mergeFluidIntoMEOutputQueueBig(output.fluidStack(), BigTstOutputLists.amount(output));
        ci.cancel();
    }
}
