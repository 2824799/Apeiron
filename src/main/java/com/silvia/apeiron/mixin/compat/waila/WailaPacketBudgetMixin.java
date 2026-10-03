package com.silvia.apeiron.mixin.compat.waila;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.silvia.apeiron.common.integration.waila.WailaNBTBudget;

import mcp.mobius.waila.network.WailaPacketHandler;

/** Covers every Waila provider, including tile entities outside GregTech and without OmniOcular. */
@Mixin(value = WailaPacketHandler.class, remap = false)
public abstract class WailaPacketBudgetMixin {

    @ModifyVariable(method = "writeNBT", at = @At("HEAD"), argsOnly = true, require = 1)
    private NBTTagCompound apeiron$boundedDisplayPacket(NBTTagCompound tag) {
        return WailaNBTBudget.limit(tag);
    }
}
