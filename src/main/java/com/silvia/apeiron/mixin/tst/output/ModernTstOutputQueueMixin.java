package com.silvia.apeiron.mixin.tst.output;

import java.math.BigInteger;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase;
import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase.FluidStackLong;
import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase.ItemStackLong;
import com.silvia.apeiron.api.machine.output.BigOutputAmount;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.api.machine.tst.BigTstOutputController;
import com.silvia.apeiron.common.machine.output.NativeOutputBatches;
import com.silvia.apeiron.common.machine.tst.output.BigTstOutputLists;

/** RC2 shares accumulating output methods between native physical and wireless recipe paths. */
@Pseudo
@Mixin(
    targets = "com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase",
    remap = false)
public abstract class ModernTstOutputQueueMixin {

    @Shadow
    @Final
    protected List<ItemStackLong> meOutputQueue;
    @Shadow
    @Final
    protected List<FluidStackLong> meFluidOutputQueue;

    @Inject(method = "mergeOutputItems(Ljava/util/List;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$mergeItems(List<ItemStackLong> outputs, CallbackInfo ci) {
        GTCM_MultiMachineBase<?> machine = (GTCM_MultiMachineBase<?>) (Object) this;
        if (!machine.isMEOutputEnabled() && !NativeOutputBatches.hasExactItems(machine)) return;
        BigTstOutputController controller = (BigTstOutputController) this;
        for (ItemStackLong output : outputs)
            controller.mergeItemIntoMEOutputQueueBig(output.itemStack(), BigTstOutputLists.amount(output));
        ci.cancel();
    }

    @Inject(method = "mergeOutputFluids(Ljava/util/List;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$mergeFluids(List<FluidStackLong> outputs, CallbackInfo ci) {
        GTCM_MultiMachineBase<?> machine = (GTCM_MultiMachineBase<?>) (Object) this;
        if (!machine.isMEOutputEnabled() && !NativeOutputBatches.hasExactFluids(machine)) return;
        BigTstOutputController controller = (BigTstOutputController) this;
        for (FluidStackLong output : outputs)
            controller.mergeFluidIntoMEOutputQueueBig(output.fluidStack(), BigTstOutputLists.amount(output));
        ci.cancel();
    }

    @Inject(method = "multiplyProcessingOutputs", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$scaleOutputs(int multiplier, CallbackInfo ci) {
        if (multiplier <= 1) return;
        BigInteger factor = BigInteger.valueOf(multiplier);
        for (ItemStackLong output : meOutputQueue) ((BigOutputAmount) (Object) output).setOutputAmountBig(
            BigTstOutputLists.amount(output)
                .multiply(factor));
        for (FluidStackLong output : meFluidOutputQueue) ((BigOutputAmount) (Object) output).setOutputAmountBig(
            BigTstOutputLists.amount(output)
                .multiply(factor));
        ((BigWirelessController) this).getWirelessRecipeState()
            .multiplyRecipeOutputs(factor);
        if (((GTCM_MultiMachineBase<?>) (Object) this).isMEOutputEnabled()) ci.cancel();
    }
}
