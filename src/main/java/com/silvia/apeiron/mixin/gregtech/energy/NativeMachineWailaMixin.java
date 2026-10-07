package com.silvia.apeiron.mixin.gregtech.energy;

import java.util.Arrays;
import java.util.Objects;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.common.integration.waila.MachineWailaAliases;
import com.silvia.apeiron.common.machine.tectech.EyeOfHarmonyWailaSnapshot;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Native Waila displays three products. Serialize only those rows, retaining the full type count. */
@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class NativeMachineWailaMixin {

    @Redirect(
        method = "getWailaNBTData",
        at = @At(
            value = "FIELD",
            target = "Lgregtech/api/metatileentity/implementations/MTEMultiBlockBase;mOutputItems:[Lnet/minecraft/item/ItemStack;"),
        require = 1)
    private ItemStack[] apeiron$boundedItems(MTEMultiBlockBase machine) {
        if (!com.silvia.apeiron.config.ApeironConfig.isLightweightWailaEnabled()) return machine.mOutputItems;
        if (machine.mOutputItems == null || machine.mOutputItems.length <= 3) return machine.mOutputItems;
        return Arrays.stream(machine.mOutputItems)
            .filter(Objects::nonNull)
            .limit(3)
            .toArray(ItemStack[]::new);
    }

    @Redirect(
        method = "getWailaNBTData",
        at = @At(
            value = "FIELD",
            target = "Lgregtech/api/metatileentity/implementations/MTEMultiBlockBase;mOutputFluids:[Lnet/minecraftforge/fluids/FluidStack;"),
        require = 1)
    private FluidStack[] apeiron$boundedFluids(MTEMultiBlockBase machine) {
        if (!com.silvia.apeiron.config.ApeironConfig.isLightweightWailaEnabled()) return machine.mOutputFluids;
        if (machine.mOutputFluids == null || machine.mOutputFluids.length <= 3) return machine.mOutputFluids;
        return Arrays.stream(machine.mOutputFluids)
            .filter(Objects::nonNull)
            .limit(3)
            .toArray(FluidStack[]::new);
    }

    @Inject(method = "getWailaNBTData", at = @At("RETURN"), require = 1)
    private void apeiron$totalTypes(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x,
        int y, int z, CallbackInfo ci) {
        MTEMultiBlockBase machine = (MTEMultiBlockBase) (Object) this;
        MachineWailaAliases.write(machine, tag);
        EyeOfHarmonyWailaSnapshot.write(machine, tag);
        if (machine.mOutputItems != null) {
            tag.setInteger(
                "outputItemLength",
                (int) Arrays.stream(machine.mOutputItems)
                    .filter(Objects::nonNull)
                    .count());
        }
        if (machine.mOutputFluids != null) {
            tag.setInteger(
                "outputFluidLength",
                (int) Arrays.stream(machine.mOutputFluids)
                    .filter(Objects::nonNull)
                    .count());
        }
    }
}
