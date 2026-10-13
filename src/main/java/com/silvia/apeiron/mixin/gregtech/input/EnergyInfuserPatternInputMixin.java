package com.silvia.apeiron.mixin.gregtech.input;

import java.util.ArrayList;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.silvia.apeiron.common.machine.input.MutablePatternItemInputs;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.check.CheckRecipeResult;
import tectech.thing.metaTileEntity.multi.MTEEnergyInfuser;

/** Use the original charger/repair/output operations over transactional, single-item pattern-buffer slots. */
@Mixin(value = MTEEnergyInfuser.class, remap = false)
public abstract class EnergyInfuserPatternInputMixin {

    @Unique
    private MutablePatternItemInputs apeiron$items;
    @Unique
    private int apeiron$itemDepth;

    @WrapMethod(method = "checkProcessing_EM")
    private CheckRecipeResult apeiron$check(Operation<CheckRecipeResult> original) {
        apeiron$itemDepth++;
        try {
            return original.call();
        } finally {
            apeiron$finishItems();
        }
    }

    @WrapMethod(method = "outputAfterRecipe_EM")
    private void apeiron$output(Operation<Void> original) {
        apeiron$itemDepth++;
        try {
            original.call();
        } finally {
            apeiron$finishItems();
        }
    }

    @WrapMethod(method = "onPostTick")
    private void apeiron$charge(IGregTechTileEntity tile, long tick, Operation<Void> original) {
        apeiron$itemDepth++;
        try {
            original.call(tile, tick);
        } finally {
            apeiron$finishItems();
        }
    }

    @Unique
    private void apeiron$finishItems() {
        if (--apeiron$itemDepth != 0 || apeiron$items == null) return;
        MutablePatternItemInputs items = apeiron$items;
        apeiron$items = null;
        items.close();
    }

    @Redirect(
        method = { "checkProcessing_EM", "outputAfterRecipe_EM", "onPostTick" },
        at = @At(
            value = "FIELD",
            target = "Ltectech/thing/metaTileEntity/multi/MTEEnergyInfuser;mInputBusses:Ljava/util/ArrayList;"),
        require = 3)
    private ArrayList<MTEHatchInputBus> apeiron$bufferSlots(MTEEnergyInfuser machine) {
        if (machine.getBaseMetaTileEntity() != null && machine.getBaseMetaTileEntity()
            .isClientSide()) return machine.mInputBusses;
        if (apeiron$items == null) apeiron$items = new MutablePatternItemInputs(machine);
        return apeiron$items.buses();
    }

    @Redirect(
        method = { "checkProcessing_EM", "outputAfterRecipe_EM" },
        at = @At(
            value = "INVOKE",
            target = "Ltectech/thing/metaTileEntity/multi/MTEEnergyInfuser;depleteInput(Lnet/minecraft/item/ItemStack;)Z"),
        require = 2)
    private boolean apeiron$consumeBufferItem(MTEEnergyInfuser machine, ItemStack stack) {
        return apeiron$items != null && apeiron$items.consume(stack) || machine.depleteInput(stack);
    }
}
