package com.silvia.apeiron.mixin.tst.output;

import java.math.BigInteger;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.Nxer.TwistSpaceTechnology.common.machine.MiscHelper;
import com.Nxer.TwistSpaceTechnology.config.Config;
import com.silvia.apeiron.api.machine.tst.BigTstOutputController;
import com.silvia.apeiron.common.machine.output.NativeOutputBatches;
import com.silvia.apeiron.math.StarcoreYield;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;

/** Keep native sampling, duration and debit, and transfer sampled identities with the exact native yield. */
@Pseudo
@Mixin(targets = "com.Nxer.TwistSpaceTechnology.common.machine.TST_StarcoreMiner", remap = false)
public abstract class StarcoreOutputBigMixin {

    @Inject(method = "checkProcessing", at = @At("RETURN"), require = 1)
    private void apeiron$queueSamples(CallbackInfoReturnable<CheckRecipeResult> cir) {
        MTEMultiBlockBase machine = (MTEMultiBlockBase) (Object) this;
        if (!cir.getReturnValue()
            .wasSuccessful() || !NativeOutputBatches.hasExactItems(machine) || machine.mOutputItems == null) return;
        ItemStack controller = machine.getControllerSlot();
        int arrays = controller != null && controller.stackSize > 0
            && MiscHelper.ASTRAL_ARRAY_FABRICATOR != null
            && MiscHelper.ASTRAL_ARRAY_FABRICATOR.isItemEqual(controller) ? controller.stackSize : 0;
        BigInteger amount = StarcoreYield.amount(Config.StackSizeOfEveryOreItemStackWhenMining_StarcoreMiner, arrays);
        BigTstOutputController outputs = (BigTstOutputController) this;
        for (ItemStack output : machine.mOutputItems)
            if (output != null) outputs.mergeItemIntoMEOutputQueueBig(output, amount);
        machine.mOutputItems = null;
    }
}
