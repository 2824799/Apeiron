package com.silvia.apeiron.mixin.gregtech.machine;

import java.util.ArrayList;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.silvia.apeiron.common.machine.block.MachineDropContents;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;

import gregtech.api.metatileentity.BaseMetaTileEntity;

@Mixin(value = BaseMetaTileEntity.class, remap = false)
public abstract class MachineDropsMixin {

    @WrapMethod(method = "getDrops")
    private ArrayList<ItemStack> apeiron$machineContents(Operation<ArrayList<ItemStack>> original) {
        BaseMetaTileEntity tile = (BaseMetaTileEntity) (Object) this;
        int id = tile.getMetaTileID();
        if (!ApeironMachines.isApeironMachine(id)) return original.call();
        NBTTagCompound contents = MachineDropContents.capture(tile);
        ArrayList<ItemStack> drops = original.call();
        MachineDropContents.finish(drops, id, contents);
        return drops;
    }
}
