package com.silvia.apeiron.common.machine.energy;

import java.math.BigInteger;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.api.machine.tst.BigTstOutputController;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** One display path for native recipes and exact wireless recipes, without client output-list callbacks. */
public final class MachineRecipeDisplay {

    private MachineRecipeDisplay() {}

    public static NBTTagCompound snapshot(MTEMultiBlockBase machine) {
        if (machine instanceof BigWirelessController && ((BigWirelessController) machine).getWirelessRecipeState()
            .isRunning()) {
            return ((BigWirelessController) machine).getWirelessRecipeState()
                .writeDisplayNBT();
        }
        BigMachineOutputQueue outputs = new BigMachineOutputQueue();
        if (machine instanceof BigTstOutputController) ((BigTstOutputController) machine).copyRecipeOutputsBig(outputs);
        if (machine.mOutputItems != null) {
            for (ItemStack stack : machine.mOutputItems) {
                if (stack != null && stack.stackSize > 0) outputs.addItem(stack, BigInteger.valueOf(stack.stackSize));
            }
        }
        if (machine.mOutputFluids != null) {
            for (FluidStack stack : machine.mOutputFluids) {
                if (stack != null && stack.amount > 0) outputs.addFluid(stack, BigInteger.valueOf(stack.amount));
            }
        }
        NBTTagCompound tag = new NBTTagCompound();
        outputs.save(tag);
        tag.setInteger("duration", machine.mMaxProgresstime);
        tag.setBoolean("running", !outputs.isEmpty());
        return tag;
    }
}
