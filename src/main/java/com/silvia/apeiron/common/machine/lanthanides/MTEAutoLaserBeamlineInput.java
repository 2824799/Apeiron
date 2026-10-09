package com.silvia.apeiron.common.machine.lanthanides;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.silvia.apeiron.common.machine.registration.ApeironMachines;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gtnhlanth.common.beamline.BeamLinePacket;
import gtnhlanth.common.hatch.MTEHatchInputBeamline;

/** A target-chamber-only input that supplies the native photon condition automatically. */
@IMetaTileEntity.SkipGenerateDescription
@IMetaTileEntity.SkipGenerateName
public class MTEAutoLaserBeamlineInput extends MTEHatchInputBeamline {

    public MTEAutoLaserBeamlineInput(int id, String name, String regionalName, int tier) {
        super(id, name, regionalName, tier);
    }

    protected MTEAutoLaserBeamlineInput(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, description, textures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEAutoLaserBeamlineInput(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public ItemStack getStackForm(long amount) {
        return new ItemStack(
            ApeironMachines.block,
            (int) Math.min(Integer.MAX_VALUE, amount),
            getBaseMetaTileEntity().getMetaTileID());
    }

    @Override
    public String getLocalName() {
        return StatCollector.translateToLocal("gt.blockmachines.apeiron.auto_laser_beamline_input.name");
    }

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("apeiron.machine.auto_laser_beamline_input.desc.1"),
            StatCollector.translateToLocal("apeiron.machine.auto_laser_beamline_input.desc.2"),
            StatCollector.translateToLocal("apeiron.machine.auto_laser_beamline_input.desc.3") };
    }

    /** This hatch is a self-contained laser source and must never consume a real beam packet. */
    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        if (tile.isServerSide()) tile.setActive(tile.isAllowedToWork());
    }

    @Override
    public void moveAround(IGregTechTileEntity tile) {}

    @Override
    public void setContents(BeamLinePacket packet) {
        dataPacket = null;
    }

    @Override
    public String[] getInfoData() {
        return getDescription();
    }
}
