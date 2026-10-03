package com.silvia.apeiron.common.machine.energy;

import java.math.BigInteger;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.silvia.apeiron.api.machine.energy.BigWirelessEnergySource;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.util.GTSplit;

/** A normal energy-hatch structure entry backed exclusively by its owner's GT wireless account. */
@IMetaTileEntity.SkipGenerateDescription
@IMetaTileEntity.SkipGenerateName
public class MTEInfiniteEnergyHatch extends MTEHatchEnergy implements BigWirelessEnergySource {

    private DirectWirelessEnergySource source;

    public MTEInfiniteEnergyHatch(int id, String name, String regionalName) {
        super(id, name, regionalName, 14);
    }

    protected MTEInfiniteEnergyHatch(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, description, textures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEInfiniteEnergyHatch(mName, mTier, mDescriptionArray, mTextures);
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
        return net.minecraft.util.StatCollector.translateToLocal("gt.blockmachines.apeiron.infinite_energy_hatch.name");
    }

    @Override
    public String[] getDescription() {
        return GTSplit.splitLocalized("apeiron.machine.infinite_energy.desc");
    }

    // TST's InfiniteWirelessMulti at tier 14 and INT_MAX amps uses this same wireless laser overlay.
    @Override
    public ITexture[] getTexturesActive(ITexture base) {
        return new ITexture[] { base, Textures.BlockIcons.OVERLAYS_ENERGY_ON_WIRELESS_LASER[15] };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture base) {
        return getTexturesActive(base);
    }

    @Override
    public long maxEUInput() {
        return Integer.MAX_VALUE;
    }

    public boolean isUltimate() {
        return false;
    }

    @Override
    public long maxAmperesIn() {
        return Integer.MAX_VALUE;
    }

    @Override
    public long maxEUStore() {
        return 0;
    }

    @Override
    public boolean isEnetInput() {
        return false;
    }

    @Override
    public boolean isElectric() {
        return true;
    }

    @Override
    public boolean isInputFacing(ForgeDirection side) {
        return false;
    }

    @Override
    public UUID getOwnerUuid() {
        return getBaseMetaTileEntity().getOwnerUuid();
    }

    private DirectWirelessEnergySource account() {
        UUID owner = getOwnerUuid();
        if (source == null || !source.getOwnerUuid()
            .equals(owner)) source = new DirectWirelessEnergySource(owner);
        return source;
    }

    @Override
    public BigInteger getAvailableEUBig() {
        return account().getAvailableEUBig();
    }

    @Override
    public boolean canConsumeEUBig(BigInteger amount) {
        return account().canConsumeEUBig(amount);
    }

    @Override
    public boolean consumeEUBig(BigInteger amount) {
        return account().consumeEUBig(amount);
    }
}
