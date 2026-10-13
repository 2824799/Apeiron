package com.silvia.apeiron.mixin.messtech;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

import com.silvia.apeiron.common.integration.messtech.MessRecipeBatch;
import com.silvia.apeiron.common.integration.messtech.MessRecipeController;

@Pseudo
@Mixin(
    targets = { "com.MessTech.common.machine.Base.MTMultiMachineBase",
        "com.MessTech.common.machine.module.SpaceModuleInfinityBase" },
    remap = false)
public abstract class MessControllerMixin implements MessRecipeController {

    @Unique
    private final MessRecipeBatch apeiron$messBatch = new MessRecipeBatch();

    @Override
    public MessRecipeBatch getMessRecipeBatch() {
        return apeiron$messBatch;
    }
}
