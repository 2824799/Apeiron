package com.silvia.apeiron.mixin.tst.energy;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import com.Nxer.TwistSpaceTechnology.common.machine.TST_MegaStoneBreaker;
import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigRecipeOutputProvider;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;

import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import gregtech.api.enums.Materials;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.util.GTRecipe;

/** Exposes the native 4x/1024x output rule; the native start hook and periodic fluid drain remain in use. */
@Mixin(value = TST_MegaStoneBreaker.class, remap = false)
public abstract class StoneBreakerEnergyMixin extends GTCM_MultiMachineBase<TST_MegaStoneBreaker>
    implements BigRecipeOutputProvider {

    @Shadow
    private MTEHatchInput mWaterHatch;
    @Shadow
    private MTEHatchInput mLavaHatch;
    @Shadow
    boolean isOutputMultiply;
    @Shadow
    private byte runningTick;
    @Unique
    private static final String APEIRON_BOOST = "ApeironStoneBreakerBoost";

    protected StoneBreakerEnergyMixin(String name) {
        super(name);
    }

    @Override
    public boolean hasExactRecipeOutputs() {
        return InfiniteEnergyHatches.find(this) != null;
    }

    @Override
    public List<IAEStack<?>> calculateRecipeOutputsBig(GTRecipe recipe, BigInteger parallels) {
        // Capacity is checked before onRecipeStart updates isOutputMultiply; simulate the same native condition.
        int multiplier = drain(mWaterHatch, new FluidStack(Materials.Water.mFluid, 1000), false)
            && drain(mLavaHatch, new FluidStack(Materials.Lava.mFluid, 1000), false) ? 1024 : 4;
        List<IAEStack<?>> outputs = new ArrayList<>();
        for (ItemStack item : recipe.mOutputs) if (item != null) outputs.add(
            BigAEStackValues.copyWithSize(
                AEItemStack.create(item),
                parallels.multiply(BigInteger.valueOf((long) item.stackSize * multiplier))));
        return outputs;
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        tag.removeTag(APEIRON_BOOST);
        if (((BigWirelessController) this).getWirelessRecipeState()
            .isRunning()) {
            NBTTagCompound boost = new NBTTagCompound();
            boost.setBoolean("multiplied", isOutputMultiply);
            boost.setByte("tick", runningTick);
            tag.setTag(APEIRON_BOOST, boost);
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        if (((BigWirelessController) this).getWirelessRecipeState()
            .isRunning() && tag.hasKey(APEIRON_BOOST, 10)) {
            NBTTagCompound boost = tag.getCompoundTag(APEIRON_BOOST);
            isOutputMultiply = boost.getBoolean("multiplied");
            runningTick = (byte) Math.max(0, Math.min(20, boost.getByte("tick")));
        }
    }
}
