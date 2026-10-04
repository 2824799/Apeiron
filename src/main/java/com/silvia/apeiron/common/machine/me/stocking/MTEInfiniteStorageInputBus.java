// SPDX-License-Identifier: GPL-3.0-only
// 360-slot stocking input adapted from GT Not Leisure; native ME connection and structure behavior remain in GT.
package com.silvia.apeiron.common.machine.me.stocking;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

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
import gregtech.common.tileentities.machines.MTEHatchInputBusME;

@IMetaTileEntity.SkipGenerateDescription
public class MTEInfiniteStorageInputBus extends MTEHatchInputBusME
    implements StockingInputHost, com.silvia.apeiron.compat.HatchWatcherHost {

    private final StockingInputLogic stocking;

    public MTEInfiniteStorageInputBus(int id, String name, String localName) {
        this(id, name, localName, StockingInputLogic.Kind.ITEMS);
    }

    protected MTEInfiniteStorageInputBus(int id, String name, String localName, StockingInputLogic.Kind kind) {
        super(id, true, name, localName);
        stocking = new StockingInputLogic(this, kind);
    }

    protected MTEInfiniteStorageInputBus(String name, int tier, String[] description, ITexture[][][] textures,
        StockingInputLogic.Kind kind) {
        super(name, true, tier, description, textures);
        stocking = new StockingInputLogic(this, kind);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEInfiniteStorageInputBus(
            mName,
            mTier,
            mDescriptionArray,
            mTextures,
            StockingInputLogic.Kind.ITEMS);
    }

    @Override
    public StockingInputLogic getStockingInput() {
        return stocking;
    }

    protected String typeKey() {
        return "infinite_storage_input_bus";
    }

    @Override
    public String getLocalName() {
        return net.minecraft.util.StatCollector.translateToLocal("gt.blockmachines.apeiron." + typeKey() + ".name");
    }

    @Override
    public String[] getDescription() {
        return GTSplit.splitLocalized("apeiron.machine." + typeKey() + ".desc");
    }

    @Override
    public ItemStack getStackForm(long amount) {
        return new ItemStack(
            ApeironMachines.block,
            (int) Math.min(Integer.MAX_VALUE, amount),
            getBaseMetaTileEntity().getMetaTileID());
    }

    @Override
    public int getSizeInventory() {
        return StockingInputLogic.SLOT_COUNT;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return getStockingInput() == null ? null : getStockingInput().itemView(slot);
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
    public boolean isValidSlot(int slot) {
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
    public ItemStack decrStackSize(int slot, int amount) {
        return getStockingInput().extractItem(slot, amount, false);
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {} // Only phantom selections and recipe debits may
                                                                       // change this input.

    @Override
    public boolean allowSelectCircuit() {
        return false;
    }

    @Override
    public void updateSlots() {}

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
    public void addWatcherCompat(Object watcher) {
        if (getStockingInput() != null) getStockingInput().addWatcher(watcher);
    }

    @Override
    public void removeWatcherCompat(Object watcher) {
        if (getStockingInput() != null) getStockingInput().removeWatcher(watcher);
    }

    @Override
    public boolean needsPeriodicChecks() {
        return true;
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        tag.removeTag("circuit");
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
