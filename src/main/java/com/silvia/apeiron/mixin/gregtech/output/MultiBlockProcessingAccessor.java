package com.silvia.apeiron.mixin.gregtech.output;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

@Mixin(value = MTEMultiBlockBase.class, remap = false)
public interface MultiBlockProcessingAccessor {

    @Accessor("processingLogic")
    ProcessingLogic apeiron$getProcessingLogic();

    @Invoker("checkRecipe")
    boolean apeiron$checkRecipe();

    @Accessor("mOutputItems")
    ItemStack[] apeiron$getOutputItems();

    @Accessor("mOutputItems")
    void apeiron$setOutputItems(ItemStack[] outputs);

    @Accessor("mOutputFluids")
    FluidStack[] apeiron$getOutputFluids();

    @Accessor("mOutputFluids")
    void apeiron$setOutputFluids(FluidStack[] outputs);
}
