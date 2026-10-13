package com.silvia.apeiron.common.machine.energy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import cofh.api.energy.IEnergyContainerItem;
import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.misc.WirelessNetworkManager;
import ic2.api.item.ElectricItem;
import ic2.api.item.IElectricItem;

/** Item charging bypasses ProcessingLogic: debit the hatch account before publishing changed item NBT. */
public final class EnergyInfuserPower {

    private static final BigInteger HUNDRED = BigInteger.valueOf(100);

    private EnergyInfuserPower() {}

    public static long available(MTEMultiBlockBase machine) {
        MTEInfiniteEnergyHatch hatch = InfiniteEnergyHatches.find(machine);
        return hatch == null ? machine.getEUVar()
            : hatch.getAvailableEUBig()
                .max(BigInteger.ZERO)
                .min(BigInteger.valueOf(Long.MAX_VALUE))
                .longValue();
    }

    public static long chargeEU(MTEMultiBlockBase machine, MTEInfiniteEnergyHatch hatch, IElectricItem item,
        ItemStack stack) {
        BigInteger balance = hatch.getAvailableEUBig()
            .max(BigInteger.ZERO);
        BigInteger power = BigInteger.valueOf(InfiniteEnergyHatches.processingVoltage(machine))
            .multiply(BigInteger.valueOf(Math.max(1, machine.getMaxInputAmps())));
        BigInteger limit = balance.min(power)
            .max(BigInteger.ZERO);
        if (limit.signum() == 0) return 0;
        double offered = limit.doubleValue();
        if (BigDecimal.valueOf(offered)
            .toBigInteger()
            .compareTo(limit) > 0) offered = Math.nextDown(offered);
        ItemStack charged = stack.copy();
        double accepted = ElectricItem.manager.charge(charged, offered, item.getTier(charged), true, false);
        if (!Double.isFinite(accepted) || accepted <= 0) return 0;
        BigInteger cost = BigDecimal.valueOf(accepted)
            .setScale(0, RoundingMode.CEILING)
            .toBigIntegerExact();
        if (cost.compareTo(limit) > 0 || !hatch.consumeEUBig(cost)) return 0;
        publish(stack, charged);
        return cost.min(BigInteger.valueOf(Long.MAX_VALUE))
            .longValue();
    }

    public static long chargeRF(MTEInfiniteEnergyHatch hatch, IEnergyContainerItem item, ItemStack stack) {
        if (GregTechAPI.mEUtoRF <= 0) return 0;
        BigInteger ratio = BigInteger.valueOf(GregTechAPI.mEUtoRF);
        BigInteger balance = hatch.getAvailableEUBig()
            .max(BigInteger.ZERO);
        long missing = (long) item.getMaxEnergyStored(stack) - item.getEnergyStored(stack);
        int offered = balance.multiply(ratio)
            .divide(HUNDRED)
            .min(BigInteger.valueOf(Math.max(0, missing)))
            .min(BigInteger.valueOf(Integer.MAX_VALUE))
            .intValue();
        if (offered <= 0) return 0;
        ItemStack charged = stack.copy();
        int accepted = item.receiveEnergy(charged, offered, false);
        if (accepted <= 0 || accepted > offered) return 0;
        BigInteger cost = BigInteger.valueOf(accepted)
            .multiply(HUNDRED)
            .add(ratio)
            .subtract(BigInteger.ONE)
            .divide(ratio);
        if (!hatch.consumeEUBig(cost)) return 0;
        publish(stack, charged);
        return cost.longValueExact();
    }

    public static boolean repair(MTEMultiBlockBase machine, MTEInfiniteEnergyHatch hatch, FluidStack uum) {
        // Native infuser repair consumes one mB of UU and 1000 EU for each restored durability point.
        // Native depletion may drain a partial quantity before returning false; preflight the entire request.
        if (!machine.depleteInput(uum, true)) return false;
        BigInteger cost = BigInteger.valueOf(Math.max(0, uum.amount))
            .multiply(BigInteger.valueOf(1000));
        if (!hatch.consumeEUBig(cost)) return false;
        boolean used = false;
        try {
            used = machine.depleteInput(uum);
            return used;
        } finally {
            if (!used && cost.signum() > 0
                && !WirelessNetworkManager.addEUToGlobalEnergyMap(hatch.getOwnerUuid(), cost))
                throw new IllegalStateException("Could not return unused infuser repair energy");
        }
    }

    private static void publish(ItemStack target, ItemStack charged) {
        target.setItemDamage(charged.getItemDamage());
        NBTTagCompound tag = charged.getTagCompound();
        target.setTagCompound(tag == null ? null : (NBTTagCompound) tag.copy());
    }
}
