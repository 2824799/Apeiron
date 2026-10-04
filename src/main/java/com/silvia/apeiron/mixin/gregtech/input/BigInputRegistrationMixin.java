package com.silvia.apeiron.mixin.gregtech.input;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.api.machine.me.input.BigDualInputHatch;
import com.silvia.apeiron.common.machine.me.stocking.MTEInfiniteStorageInputAssembly;
import com.silvia.apeiron.common.machine.me.stocking.MTEInfiniteStorageInputHatch;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class BigInputRegistrationMixin {

    @Inject(
        method = { "addToMachineList", "addInputBusToMachineList", "addInputHatchToMachineList" },
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$deduplicateBigInput(IGregTechTileEntity tile, int texture,
        CallbackInfoReturnable<Boolean> cir) {
        if (tile != null && tile.getMetaTileEntity() instanceof MTEInfiniteStorageInputAssembly) {
            MTEInfiniteStorageInputAssembly assembly = (MTEInfiniteStorageInputAssembly) tile.getMetaTileEntity();
            MTEMultiBlockBase controller = (MTEMultiBlockBase) (Object) this;
            MTEInfiniteStorageInputHatch fluids = assembly.getFluidInput();
            assembly.updateTexture(texture);
            assembly.updateCraftingIcon(controller.getMachineCraftingIcon());
            assembly.mRecipeMap = controller.getRecipeMap();
            fluids.updateTexture(texture);
            fluids.updateCraftingIcon(controller.getMachineCraftingIcon());
            fluids.mRecipeMap = controller.getRecipeMap();
            // A storage assembly has no isolated pattern inventories. Register it as ordinary
            // item/fluid inputs so legacy generators see it and structure counts stay at one each.
            controller.mDualInputHatches.remove(assembly);
            if (!controller.mInputBusses.contains(assembly)) {
                controller.mInputBusses.add(assembly);
                com.silvia.apeiron.compat.HatchNotifications.registerSmartInput(controller, assembly);
            }
            if (!controller.mInputHatches.contains(fluids)) controller.mInputHatches.add(fluids);
            cir.setReturnValue(true);
            return;
        }
        if (tile == null || !(tile.getMetaTileEntity() instanceof BigDualInputHatch)) return;
        gregtech.common.tileentities.machines.IDualInputHatch hatch = (gregtech.common.tileentities.machines.IDualInputHatch) tile
            .getMetaTileEntity();
        MTEMultiBlockBase controller = (MTEMultiBlockBase) (Object) this;
        if (controller.mDualInputHatches.contains(hatch)) {
            hatch.updateTexture(texture);
            hatch.updateCraftingIcon(controller.getMachineCraftingIcon());
            cir.setReturnValue(true);
        }
    }
}
