package com.silvia.apeiron.mixin.tst.output;

import java.math.BigInteger;
import java.util.Map;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.Nxer.TwistSpaceTechnology.common.api.giver.ItemStacksGiver;
import com.Nxer.TwistSpaceTechnology.util.rewrites.TST_ItemID;
import com.silvia.apeiron.api.machine.tst.BigTstItemGiver;
import com.silvia.apeiron.api.machine.tst.BigTstOutputController;
import com.silvia.apeiron.common.machine.output.NativeOutputBatches;

import gregtech.api.enums.GTValues;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Read the native collection before toArray expands it into repeated Integer.MAX_VALUE stacks. */
@Pseudo
@Mixin(targets = "com.Nxer.TwistSpaceTechnology.system.OreProcess.machines.TST_OreProcessingFactory", remap = false)
public abstract class OreProcessingOutputBigMixin {

    @Redirect(
        method = { "OP_Process_Wireless", "OP_Process_Normal" },
        at = @At(
            value = "INVOKE",
            target = "Lcom/Nxer/TwistSpaceTechnology/common/api/giver/ItemStacksGiver;toArray()[Lnet/minecraft/item/ItemStack;"),
        require = 2)
    private ItemStack[] apeiron$keepCounts(ItemStacksGiver giver) {
        MTEMultiBlockBase machine = (MTEMultiBlockBase) (Object) this;
        if (!NativeOutputBatches.hasExactItems(machine)) return giver.toArray();
        BigTstOutputController outputs = (BigTstOutputController) this;
        for (Map.Entry<TST_ItemID, BigInteger> entry : ((BigTstItemGiver) giver).getItemAmountsBig()
            .entrySet())
            if (entry.getValue()
                .signum() > 0)
                outputs.mergeItemIntoMEOutputQueueBig(
                    entry.getKey()
                        .getItemStack(1),
                    entry.getValue());
        return GTValues.emptyItemStackArray;
    }

    @Redirect(
        method = { "checkProcessing_wirelessMode", "checkProcessing_normalMode" },
        at = @At(
            value = "FIELD",
            target = "Lcom/Nxer/TwistSpaceTechnology/system/OreProcess/machines/TST_OreProcessingFactory;mOutputItems:[Lnet/minecraft/item/ItemStack;"),
        require = 2)
    private ItemStack[] apeiron$hasOutputs(
        com.Nxer.TwistSpaceTechnology.system.OreProcess.machines.TST_OreProcessingFactory machine) {
        return ((BigTstOutputController) machine).getRecipeItemOutputBig()
            .signum() > 0 ? new ItemStack[1] : machine.mOutputItems;
    }
}
