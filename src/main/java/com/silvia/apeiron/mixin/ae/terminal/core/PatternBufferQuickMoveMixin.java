package com.silvia.apeiron.mixin.ae.terminal.core;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.screen.ModularContainer;
import com.silvia.apeiron.common.machine.me.input.storage.PatternBufferQuickMove;

@Mixin(value = ModularContainer.class, remap = false)
public abstract class PatternBufferQuickMoveMixin {

    @Inject(method = { "transferStackInSlot", "func_82846_b" }, at = @At("HEAD"), cancellable = true)
    private void apeiron$deposit(EntityPlayer player, int index, CallbackInfoReturnable<ItemStack> cir) {
        ModularContainer container = (ModularContainer) (Object) this;
        if (index < 0 || index >= container.inventorySlots.size()) return;
        Slot slot = (Slot) container.inventorySlots.get(index);
        if (PatternBufferQuickMove.handles(container, slot, player))
            cir.setReturnValue(PatternBufferQuickMove.transfer(container, slot, player));
    }
}
