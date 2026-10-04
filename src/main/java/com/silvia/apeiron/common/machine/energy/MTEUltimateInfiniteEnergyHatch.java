package com.silvia.apeiron.common.machine.energy;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTSplit;

@IMetaTileEntity.SkipGenerateDescription
@IMetaTileEntity.SkipGenerateName
public final class MTEUltimateInfiniteEnergyHatch extends MTEInfiniteEnergyHatch {

    private static final gregtech.api.interfaces.IIconContainer OVERLAY = Textures.BlockIcons
        .custom("apeiron:energy/ultimate_infinite_energy");

    public MTEUltimateInfiniteEnergyHatch(int id, String name, String regionalName) {
        super(id, name, regionalName);
    }

    private MTEUltimateInfiniteEnergyHatch(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, description, textures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEUltimateInfiniteEnergyHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public String getLocalName() {
        return net.minecraft.util.StatCollector
            .translateToLocal("gt.blockmachines.apeiron.ultimate_infinite_energy_hatch.name");
    }

    @Override
    public String[] getDescription() {
        return GTSplit.splitLocalized("apeiron.machine.ultimate_energy.desc");
    }

    @Override
    public boolean isUltimate() {
        return true;
    }

    @Override
    public ITexture[] getTexturesActive(ITexture base) {
        return new ITexture[] { base, TextureFactory.builder()
            .addIcon(OVERLAY)
            .glow()
            .build() };
    }
}
