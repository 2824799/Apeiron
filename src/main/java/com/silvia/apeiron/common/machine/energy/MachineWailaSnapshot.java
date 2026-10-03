package com.silvia.apeiron.common.machine.energy;

import java.math.BigInteger;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import com.silvia.apeiron.api.machine.parallel.BigWirelessController;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** OmniOcular needs operating information, not a disk-save image containing inventories and output ledgers. */
public final class MachineWailaSnapshot {

    private MachineWailaSnapshot() {}

    public static boolean write(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag) {
        if (!(tile instanceof IGregTechTileEntity)) return false;
        IGregTechTileEntity base = (IGregTechTileEntity) tile;
        if (!(base.getMetaTileEntity() instanceof MTEMultiBlockBase)) return false;
        MTEMultiBlockBase machine = (MTEMultiBlockBase) base.getMetaTileEntity();
        boolean wireless = machine instanceof BigWirelessController
            && ((BigWirelessController) machine).getWirelessRecipeState()
                .isRunning();
        boolean factory = machine.getClass()
            .getName()
            .equals("com.Nxer.TwistSpaceTechnology.system.OreProcess.machines.TST_OreProcessingFactory");
        if (!wireless && !factory
            && (machine.mOutputItems == null || machine.mOutputItems.length <= 64)
            && (machine.mOutputFluids == null || machine.mOutputFluids.length <= 64)) return false;
        machine.getWailaNBTData(player, tile, tag, tile.getWorldObj(), tile.xCoord, tile.yCoord, tile.zCoord);
        tag.setInteger("mID", base.getMetaTileID());
        tag.setBoolean("mActive", base.isActive());
        tag.setBoolean("mWorks", base.isAllowedToWork());
        long energy = tag.getLong("energyUsage");
        if (wireless && tag.hasKey("ApeironActualWirelessEUt")) {
            energy = new BigInteger(tag.getString("ApeironActualWirelessEUt")).min(BigInteger.valueOf(Long.MAX_VALUE))
                .longValue();
        }
        tag.setLong("mEUt", energy != 0 ? -energy : machine.mEUt);
        tag.setInteger("mEfficiency", machine.mEfficiency);
        tag.setInteger("mProgresstime", machine.mProgresstime);
        tag.setInteger("mMaxProgresstime", machine.mMaxProgresstime);
        tag.setBoolean("mWrench", machine.mWrench);
        tag.setBoolean("mScrewdriver", machine.mScrewdriver);
        tag.setBoolean("mSoftHammer", machine.mSoftMallet);
        tag.setBoolean("mHardHammer", machine.mHardHammer);
        tag.setBoolean("mSolderingTool", machine.mSolderingTool);
        tag.setBoolean("mCrowbar", machine.mCrowbar);
        tag.setTag("Inventory", new NBTTagList());
        tag.setInteger("mOutputItemsLength", 0);
        tag.setInteger("mOutputFluidsLength", 0);
        return true;
    }
}
