package com.silvia.apeiron.mixin.gregtech.energy;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.silvia.apeiron.common.machine.energy.EnergyInfuserPower;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;

import cofh.api.energy.IEnergyContainerItem;
import ic2.api.item.IElectricItem;
import tectech.thing.metaTileEntity.multi.MTEEnergyInfuser;

/** Native item operations use a local EU buffer; Apeiron hatches instead own a direct wireless account. */
@Mixin(value = MTEEnergyInfuser.class, remap = false)
public abstract class EnergyInfuserWirelessMixin {

    @Redirect(
        method = { "checkProcessing_EM", "outputAfterRecipe_EM" },
        at = @At(value = "INVOKE", target = "Ltectech/thing/metaTileEntity/multi/MTEEnergyInfuser;getEUVar()J"),
        require = 3)
    private long apeiron$available(MTEEnergyInfuser machine) {
        return EnergyInfuserPower.available(machine);
    }

    @WrapMethod(method = "doChargeItemStack")
    private long apeiron$chargeEU(IElectricItem item, ItemStack stack, Operation<Long> original) {
        MTEEnergyInfuser machine = (MTEEnergyInfuser) (Object) this;
        MTEInfiniteEnergyHatch hatch = InfiniteEnergyHatches.find(machine);
        if (hatch == null) return original.call(item, stack);
        try {
            return EnergyInfuserPower.chargeEU(machine, hatch, item, stack);
        } catch (Exception failure) {
            if (tectech.loader.ConfigHandler.debug.DEBUG_MODE) tectech.TecTech.LOGGER.error(failure);
            return 0;
        }
    }

    @WrapMethod(method = "doChargeItemStackRF")
    private long apeiron$chargeRF(IEnergyContainerItem item, ItemStack stack, Operation<Long> original) {
        MTEInfiniteEnergyHatch hatch = InfiniteEnergyHatches.find((MTEEnergyInfuser) (Object) this);
        if (hatch == null) return original.call(item, stack);
        try {
            return EnergyInfuserPower.chargeRF(hatch, item, stack);
        } catch (Exception failure) {
            if (tectech.loader.ConfigHandler.debug.DEBUG_MODE) tectech.TecTech.LOGGER.error(failure);
            return 0;
        }
    }

    @Redirect(
        method = "outputAfterRecipe_EM",
        at = @At(
            value = "INVOKE",
            target = "Ltectech/thing/metaTileEntity/multi/MTEEnergyInfuser;depleteInput(Lnet/minecraftforge/fluids/FluidStack;)Z"),
        require = 1)
    private boolean apeiron$repairDebit(MTEEnergyInfuser machine, FluidStack uum) {
        MTEInfiniteEnergyHatch hatch = InfiniteEnergyHatches.find(machine);
        return hatch == null ? machine.depleteInput(uum) : EnergyInfuserPower.repair(machine, hatch, uum);
    }

    @Redirect(
        method = "outputAfterRecipe_EM",
        at = @At(value = "INVOKE", target = "Ltectech/thing/metaTileEntity/multi/MTEEnergyInfuser;setEUVar(J)V"),
        require = 1)
    private void apeiron$keepLocalBuffer(MTEEnergyInfuser machine, long remaining) {
        if (InfiniteEnergyHatches.find(machine) == null) machine.setEUVar(remaining);
    }
}
