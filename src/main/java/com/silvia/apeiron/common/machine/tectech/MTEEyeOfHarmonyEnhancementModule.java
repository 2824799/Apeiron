package com.silvia.apeiron.common.machine.tectech;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.silvia.apeiron.client.gui.machine.tectech.EyeOfHarmonyEnhancementModuleGui;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTSplit;

/** A configurable item module that can be installed into TecTech's Eye of Harmony. */
public final class MTEEyeOfHarmonyEnhancementModule extends MTEHatchInput {

    public static final String ROOT_TAG = "ApeironEyeOfHarmonyEnhancement";
    public static final String DURATION_TAG = "Duration";
    public static final String SUCCESS_CHANCE_TAG = "SuccessChance";
    public static final int DEFAULT_DURATION = 1;
    public static final double DEFAULT_SUCCESS_CHANCE = 1.0D;

    @SideOnly(Side.CLIENT)
    private static ITexture moduleTexture;

    private int duration = DEFAULT_DURATION;
    private double successChance = DEFAULT_SUCCESS_CHANCE;

    public MTEEyeOfHarmonyEnhancementModule(int id, String name, String localName) {
        super(id, 3, name, localName, 10, null);
    }

    protected MTEEyeOfHarmonyEnhancementModule(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, 3, tier, description, textures);
    }

    public static int readDuration(ItemStack stack) {
        return clampDuration(
            readRoot(stack).hasKey(DURATION_TAG) ? readRoot(stack).getInteger(DURATION_TAG) : DEFAULT_DURATION);
    }

    public static double readSuccessChance(ItemStack stack) {
        NBTTagCompound root = readRoot(stack);
        return clampSuccessChance(
            root.hasKey(SUCCESS_CHANCE_TAG) ? root.getDouble(SUCCESS_CHANCE_TAG) : DEFAULT_SUCCESS_CHANCE);
    }

    public static void writeSettings(NBTTagCompound tag, int duration, double successChance) {
        NBTTagCompound root = tag.getCompoundTag(ROOT_TAG);
        root.setInteger(DURATION_TAG, clampDuration(duration));
        root.setDouble(SUCCESS_CHANCE_TAG, clampSuccessChance(successChance));
        tag.setTag(ROOT_TAG, root);
    }

    private static NBTTagCompound readRoot(ItemStack stack) {
        return stack != null && stack.hasTagCompound() ? stack.getTagCompound()
            .getCompoundTag(ROOT_TAG) : new NBTTagCompound();
    }

    private static NBTTagCompound readRoot(NBTTagCompound tag) {
        return tag == null ? new NBTTagCompound() : tag.getCompoundTag(ROOT_TAG);
    }

    public static int clampDuration(int value) {
        return Math.max(1, value);
    }

    public static double clampSuccessChance(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return DEFAULT_SUCCESS_CHANCE;
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int value) {
        duration = clampDuration(value);
        markDirty();
    }

    public double getSuccessChance() {
        return successChance;
    }

    public void setSuccessChance(double value) {
        successChance = clampSuccessChance(value);
        markDirty();
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEEyeOfHarmonyEnhancementModule(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public String getLocalName() {
        return StatCollector.translateToLocal("gt.blockmachines.apeiron.eye_of_harmony_enhancement_module.name");
    }

    @Override
    public String[] getDescription() {
        return GTSplit.splitLocalized("apeiron.machine.eye_of_harmony_enhancement_module.desc");
    }

    @Override
    public ItemStack getStackForm(long amount) {
        return new ItemStack(
            ApeironMachines.block,
            (int) Math.min(Integer.MAX_VALUE, amount),
            getBaseMetaTileEntity().getMetaTileID());
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
        return new EyeOfHarmonyEnhancementModuleGui(this).build(data, sync, settings);
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        writeSettings(tag, duration, successChance);
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        NBTTagCompound root = readRoot(tag);
        duration = clampDuration(root.hasKey(DURATION_TAG) ? root.getInteger(DURATION_TAG) : DEFAULT_DURATION);
        successChance = clampSuccessChance(
            root.hasKey(SUCCESS_CHANCE_TAG) ? root.getDouble(SUCCESS_CHANCE_TAG) : DEFAULT_SUCCESS_CHANCE);
    }

    @Override
    public void setItemNBT(NBTTagCompound tag) {
        super.setItemNBT(tag);
        if (duration != DEFAULT_DURATION || successChance != DEFAULT_SUCCESS_CHANCE)
            writeSettings(tag, duration, successChance);
        else tag.removeTag(ROOT_TAG);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister iconRegister) {
        super.registerIcons(iconRegister);
        IIconContainer icon = Textures.BlockIcons.custom("apeiron", "eye_of_harmony_enhancement_module");
        moduleTexture = TextureFactory.of(icon);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity tile, ForgeDirection side, ForgeDirection facing, int colorIndex,
        boolean active, boolean redstone) {
        ITexture base = Textures.BlockIcons.MACHINE_CASINGS[mTier][colorIndex + 1];
        return side == facing && moduleTexture != null ? new ITexture[] { base, moduleTexture }
            : new ITexture[] { base };
    }

    @Override
    public ITexture[] getTexturesActive(ITexture base) {
        return moduleTexture == null ? new ITexture[] { base } : new ITexture[] { base, moduleTexture };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture base) {
        return moduleTexture == null ? new ITexture[] { base } : new ITexture[] { base, moduleTexture };
    }
}
