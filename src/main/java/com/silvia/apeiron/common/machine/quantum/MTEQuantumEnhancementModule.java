package com.silvia.apeiron.common.machine.quantum;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.silvia.apeiron.client.gui.machine.quantum.QuantumEnhancementModuleGui;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchBulkCatalystHousing;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTSplit;

/** A stateless QFT module accepted by the native bottom-layer catalyst-housing structure element. */
@IMetaTileEntity.SkipGenerateDescription
@IMetaTileEntity.SkipGenerateName
public final class MTEQuantumEnhancementModule extends MTEHatchBulkCatalystHousing {

    @SideOnly(Side.CLIENT)
    private static ITexture overlay;

    public MTEQuantumEnhancementModule(int id, String name, String localName) {
        super(id, name, localName, 10, 0);
    }

    private MTEQuantumEnhancementModule(String name, String[] description, ITexture[][][] textures, int tier) {
        super(name, description, textures, tier, 0);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEQuantumEnhancementModule(mName, mDescriptionArray, mTextures, mTier);
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
        return StatCollector.translateToLocal("gt.blockmachines.apeiron.quantum_enhancement_module.name");
    }

    @Override
    public String[] getDescription() {
        return GTSplit.splitLocalized("apeiron.machine.quantum_enhancement_module.desc");
    }

    @Override
    public boolean isValidItem(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity tile, int slot, ForgeDirection side, ItemStack stack) {
        return false;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity tile, int slot, ForgeDirection side, ItemStack stack) {
        return false;
    }

    @Override
    protected boolean useMui2() {
        return true;
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity tile, EntityPlayer player) {
        openGui(player);
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        return new QuantumEnhancementModuleGui(this).build(data, sync, settings);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister icons) {
        super.registerIcons(icons);
        overlay = TextureFactory.of(Textures.BlockIcons.custom("apeiron", "quantum_enhancement_module"));
    }

    @Override
    public ITexture[] getTexturesActive(ITexture base) {
        return overlay == null ? new ITexture[] { base } : new ITexture[] { base, overlay };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture base) {
        return getTexturesActive(base);
    }
}
