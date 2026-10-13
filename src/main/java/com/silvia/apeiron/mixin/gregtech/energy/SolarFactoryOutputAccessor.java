package com.silvia.apeiron.mixin.gregtech.energy;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "gregtech.common.tileentities.machines.multi.MTESolarFactory", remap = false)
public interface SolarFactoryOutputAccessor {

    @Invoker("calculateNewOutput")
    ItemStack[] apeiron$nativeOutput(ItemStack output, int waferTierDifference);
}
