package com.silvia.apeiron.mixin.messtech;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.common.integration.messtech.MessNanoController;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

@Pseudo
@Mixin(targets = "com.MessTech.common.machine.Base.TickableParallelismAcrossMultiMachineBase", remap = false)
public abstract class MessThreadMixin {

    @Inject(method = "tickThread", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$tick(IGregTechTileEntity base, @Coerce Object thread, long tick, CallbackInfo ci) {
        if (this instanceof MessNanoController
            && (InfiniteEnergyHatches.find((MTEMultiBlockBase) (Object) this) != null
                || ((MessNanoController) this).getMessNanoTasks()
                    .threadState(thread) != null)
            && ((MessNanoController) this).getMessNanoTasks()
                .tick((MTEMultiBlockBase) (Object) this, thread, base))
            ci.cancel();
    }

    @Inject(method = "clearAllTasks", at = @At("HEAD"), require = 1)
    private void apeiron$clear(CallbackInfo ci) {
        if (this instanceof MessNanoController) ((MessNanoController) this).getMessNanoTasks()
            .clear();
    }
}
