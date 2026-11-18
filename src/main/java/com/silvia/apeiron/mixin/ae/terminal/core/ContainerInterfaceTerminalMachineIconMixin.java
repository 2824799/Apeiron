package com.silvia.apeiron.mixin.ae.terminal.core;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import appeng.api.util.IInterfaceViewable;
import appeng.container.implementations.ContainerInterfaceTerminal;
import appeng.helpers.ICustomNameObject;
import gregtech.api.metatileentity.implementations.MTEHatch;

@Mixin(value = ContainerInterfaceTerminal.class, remap = false)
public abstract class ContainerInterfaceTerminalMachineIconMixin {

    @WrapOperation(
        method = "updateList",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/util/IInterfaceViewable;getDisplayRep()Lnet/minecraft/item/ItemStack;"),
        require = 2)
    private ItemStack apeiron$includeMachineIcon(IInterfaceViewable machine, Operation<ItemStack> original) {
        ItemStack icon = original.call(machine);
        if (icon != null || !(machine instanceof MTEHatch hatch)
            || machine instanceof ICustomNameObject named && named.hasCustomName()) return icon;

        // Crafting hatches supply the provider icon but often omit the attached machine's representation.
        ItemStack target = hatch.getMachineCraftingIcon();
        return target != null && target.getUnlocalizedName()
            .equals(machine.getRawName()) ? target : null;
    }
}
