package com.silvia.apeiron.mixin.gregtech.output;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.api.machine.tst.BigTstOutputController;

import gregtech.api.metatileentity.CommonMetaTileEntity;

@Mixin(value = CommonMetaTileEntity.class, remap = false)
public abstract class PendingBigOutputDropMixin {

    @Inject(method = "setItemNBT", at = @At("RETURN"), require = 1)
    private void apeiron$keepProducedOutputs(NBTTagCompound tag, CallbackInfo ci) {
        if (this instanceof BigTstOutputController) ((BigTstOutputController) this).saveProducedOutputsBig(tag);
        if (this instanceof com.silvia.apeiron.api.machine.parallel.BigWirelessController) {
            final com.silvia.apeiron.common.machine.parallel.WirelessRecipeState state = ((com.silvia.apeiron.api.machine.parallel.BigWirelessController) this)
                .getWirelessRecipeState();
            tag.removeTag("ApeironWirelessRecipe");
            NBTTagCompound produced = state.saveProduced();
            if (!produced.hasNoTags()) tag.setTag("ApeironWirelessRecipe", produced);
        }
    }
}
