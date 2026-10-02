package com.silvia.apeiron.mixin.gregtech.output;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

@Mixin(value = MTEMultiBlockBase.class, remap = false)
public interface MultiBlockProcessingAccessor {

    @Accessor("processingLogic")
    ProcessingLogic apeiron$getProcessingLogic();
}
