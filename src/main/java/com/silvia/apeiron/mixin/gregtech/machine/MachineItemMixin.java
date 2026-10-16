package com.silvia.apeiron.mixin.gregtech.machine;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.common.machine.block.MachineItemNbt;

import gregtech.common.blocks.ItemMachines;

@Mixin(value = ItemMachines.class, remap = false)
public abstract class MachineItemMixin {

    @Inject(method = { "onUpdate", "func_77663_a" }, at = @At("HEAD"))
    private void apeiron$migrateMachine(ItemStack stack, World world, Entity entity, int slot, boolean held,
        CallbackInfo ci) {
        if (!world.isRemote) MachineItemNbt.migrate(stack);
    }
}
