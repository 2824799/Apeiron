// SPDX-License-Identifier: GPL-3.0-only
// 360-slot fluid stocking input adapted from GT Not Leisure's SuperInputHatchME.
package com.silvia.apeiron.common.machine.me.stocking;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.silvia.apeiron.client.gui.machine.me.input.InfiniteStorageInputGui;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTSplit;
import gregtech.common.tileentities.machines.IHatchWatcher;
import gregtech.common.tileentities.machines.MTEHatchInputME;

@IMetaTileEntity.SkipGenerateDescription
public class MTEInfiniteStorageInputHatch extends MTEHatchInputME implements StockingInputHost {

    private final StockingInputLogic stocking = new StockingInputLogic(this, StockingInputLogic.Kind.FLUIDS);

    public MTEInfiniteStorageInputHatch(int id, String name, String localName) {
        super(id, true, name, localName);
    }

    protected MTEInfiniteStorageInputHatch(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, true, tier, description, textures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEInfiniteStorageInputHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public StockingInputLogic getStockingInput() {
        return stocking;
    }

    @Override
    public String getLocalName() {
        return net.minecraft.util.StatCollector
            .translateToLocal("gt.blockmachines.apeiron.infinite_storage_input_hatch.name");
    }

    @Override
    public String[] getDescription() {
        return GTSplit.splitLocalized("apeiron.machine.infinite_storage_input_hatch.desc");
    }

    @Override
    public ItemStack getStackForm(long amount) {
        return new ItemStack(
            ApeironMachines.block,
            (int) Math.min(Integer.MAX_VALUE, amount),
            getBaseMetaTileEntity().getMetaTileID());
    }

    @Override
    public FluidStack[] getStoredFluids() {
        return getStockingInput().fluidViews();
    }

    @Override
    public FluidStack drain(ForgeDirection side, FluidStack fluid, boolean doDrain) {
        return drain(side, fluid, fluid == null ? 0 : fluid.amount, doDrain);
    }

    @Override
    public FluidStack drain(ForgeDirection side, FluidStack fluid, int amount, boolean doDrain) {
        return side == ForgeDirection.UNKNOWN ? getStockingInput().drainFluid(fluid, amount, doDrain) : null;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection side) {
        if (side != ForgeDirection.UNKNOWN) return new FluidTankInfo[0];
        FluidStack[] fluids = getStoredFluids();
        FluidTankInfo[] tanks = new FluidTankInfo[fluids.length];
        for (int i = 0; i < fluids.length; i++) tanks[i] = new FluidTankInfo(fluids[i], Integer.MAX_VALUE);
        return tanks;
    }

    @Override
    public ItemStack[] getRealInventory() {
        return new ItemStack[0];
    }

    @Override
    public boolean shouldDropItemAt(int slot) {
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity tile, int slot, net.minecraftforge.common.util.ForgeDirection side,
        ItemStack stack) {
        return false;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity tile, int slot,
        net.minecraftforge.common.util.ForgeDirection side, ItemStack stack) {
        return false;
    }

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        if (tile.isServerSide()) {
            getStockingInput().tick(tick);
            tile.setActive(getProxy().isActive() && tile.isAllowedToWork());
        }
    }

    @Override
    public void startRecipeProcessing() {
        getStockingInput().begin();
    }

    @Override
    public CheckRecipeResult endRecipeProcessing(MTEMultiBlockBase controller) {
        return getStockingInput().end();
    }

    @Override
    public void addWatcher(IHatchWatcher watcher) {
        if (getStockingInput() != null) getStockingInput().addWatcher(watcher);
    }

    @Override
    public void removeWatcher(IHatchWatcher watcher) {
        if (getStockingInput() != null) getStockingInput().removeWatcher(watcher);
    }

    @Override
    public boolean needsPeriodicChecks() {
        return true;
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        getStockingInput().save(tag);
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        getStockingInput().load(tag);
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        return new InfiniteStorageInputGui(this).build(data, sync, settings);
    }

    @Override
    public String getCopiedDataIdentifier(net.minecraft.entity.player.EntityPlayer player) {
        return "apeiron.stocking." + getStockingInput().getKind()
            .name();
    }

    @Override
    public NBTTagCompound getCopiedData(net.minecraft.entity.player.EntityPlayer player) {
        return getStockingInput().copyConfiguration();
    }

    @Override
    public boolean pasteCopiedData(net.minecraft.entity.player.EntityPlayer player, NBTTagCompound tag) {
        return getStockingInput().pasteConfiguration(tag);
    }
}
