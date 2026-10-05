// SPDX-License-Identifier: MIT
// Coordinate linking and inventory sharing adapted from Programmable Hatches (c) 2024 reobf.
package com.silvia.apeiron.common.machine.me.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.silvia.apeiron.api.machine.me.input.BigDualInputHatch;
import com.silvia.apeiron.client.gui.machine.me.input.InfinitePatternInputGui;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Textures.BlockIcons;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTSplit;
import gregtech.common.tileentities.machines.IDualInputInventory;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

@IMetaTileEntity.SkipGenerateDescription
public class MTEInfinitePatternInputMirror extends MTEHatchInputBus
    implements BigDualInputHatch, com.silvia.apeiron.compat.HatchWatcherHost {

    private boolean linked;
    private int dimension, sourceX, sourceY, sourceZ;
    private MTEInfinitePatternInputAssembly lastSource;
    private MTEInfinitePatternInputAssembly uiPreview;
    private final List<Object> forwarding = new ArrayList<>();

    public MTEInfinitePatternInputMirror(int id, String name, String regionalName) {
        super(id, name, regionalName, 10, 0);
        disableSort = true;
    }

    protected MTEInfinitePatternInputMirror(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, 0, description, textures);
        disableSort = true;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEInfinitePatternInputMirror(mName, mTier, mDescriptionArray, mTextures);
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
        return net.minecraft.util.StatCollector
            .translateToLocal("gt.blockmachines.apeiron.infinite_pattern_input_mirror.name");
    }

    @Override
    public String[] getDescription() {
        return GTSplit.splitLocalized("apeiron.machine.infinite_pattern_mirror.desc");
    }

    @Override
    public boolean allowSelectCircuit() {
        return false;
    }

    @Override
    public ITexture[] getTexturesActive(ITexture base) {
        return new ITexture[] { base, TextureFactory.of(BlockIcons.OVERLAY_ME_CRAFTING_INPUT_SLAVE) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture base) {
        return new ITexture[] { base, TextureFactory.of(BlockIcons.OVERLAY_ME_CRAFTING_INPUT_SLAVE) };
    }

    public void setLink(int dimension, int x, int y, int z) {
        detach();
        linked = true;
        this.dimension = dimension;
        sourceX = x;
        sourceY = y;
        sourceZ = z;
        markDirty();
        getInputSource();
    }

    public void clearLink() {
        detach();
        linked = false;
        markDirty();
    }

    protected MTEInfinitePatternInputAssembly lookupSource() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (!linked || tile == null
            || tile.getWorld() == null
            || tile.getWorld().provider.dimensionId != dimension
            || !tile.getWorld()
                .blockExists(sourceX, sourceY, sourceZ))
            return null;
        TileEntity host = tile.getWorld()
            .getTileEntity(sourceX, sourceY, sourceZ);
        if (!(host instanceof IGregTechTileEntity) || host.isInvalid()) return null;
        IMetaTileEntity meta = ((IGregTechTileEntity) host).getMetaTileEntity();
        return meta instanceof MTEInfinitePatternInputAssembly ? (MTEInfinitePatternInputAssembly) meta : null;
    }

    @Override
    public MTEInfinitePatternInputAssembly getInputSource() {
        MTEInfinitePatternInputAssembly source = linked ? lookupSource() : null;
        if (source != lastSource) {
            detach();
            lastSource = source;
            if (source != null) for (Object watcher : forwarding) source.addWatcherCompat(watcher);
        }
        return source;
    }

    private void detach() {
        if (lastSource != null) for (Object watcher : forwarding) lastSource.removeWatcherCompat(watcher);
        lastSource = null;
    }

    @Override
    public void addWatcherCompat(Object watcher) {
        if (forwarding.contains(watcher)) return;
        MTEInfinitePatternInputAssembly source = getInputSource();
        forwarding.add(watcher);
        if (source != null) source.addWatcherCompat(watcher);
    }

    @Override
    public void removeWatcherCompat(Object watcher) {
        if (forwarding.remove(watcher) && lastSource != null) lastSource.removeWatcherCompat(watcher);
    }

    @Override
    public void onRemoval() {
        detach();
        super.onRemoval();
    }

    @Override
    public void onUnload() {
        detach();
        super.onUnload();
    }

    @Override
    public Iterator<? extends IDualInputInventory> inventories() {
        MTEInfinitePatternInputAssembly source = getInputSource();
        return source == null ? Collections.emptyIterator() : source.inventories();
    }

    @Override
    public Optional<IDualInputInventory> getFirstNonEmptyInventory() {
        MTEInfinitePatternInputAssembly source = getInputSource();
        return source == null ? Optional.empty() : source.getFirstNonEmptyInventory();
    }

    @Override
    public boolean supportsFluids() {
        return true;
    }

    @Override
    public ItemStack[] getSharedItems() {
        return new ItemStack[0];
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
    public boolean onRightclick(IGregTechTileEntity tile, EntityPlayer player) {
        if (tile.isClientSide()) return true;
        ItemStack stick = player.inventory.getCurrentItem();
        if (stick != null && ItemList.Tool_DataStick.isStackEqual(stick, false, true)
            && stick.hasTagCompound()
            && stick.getTagCompound()
                .getString("type")
                .equals("ApeironInfinitePatternInput")) {
            NBTTagCompound tag = stick.getTagCompound();
            setLink(tag.getInteger("dimension"), tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"));
            player.addChatMessage(
                new ChatComponentTranslation(
                    getInputSource() == null ? "apeiron.machine.pattern_input.link_unavailable"
                        : "apeiron.machine.pattern_input.link_success"));
            return true;
        }
        if (getInputSource() == null) {
            player.addChatMessage(new ChatComponentTranslation("apeiron.machine.pattern_input.link_required"));
            return true;
        }
        openGui(player);
        return true;
    }

    private void writeLink(NBTTagCompound tag) {
        tag.setBoolean("ApeironInputLinked", linked);
        tag.setInteger("ApeironInputDimension", dimension);
        tag.setInteger("ApeironInputX", sourceX);
        tag.setInteger("ApeironInputY", sourceY);
        tag.setInteger("ApeironInputZ", sourceZ);
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        writeLink(tag);
    }

    @Override
    public void setItemNBT(NBTTagCompound tag) {
        super.setItemNBT(tag);
        for (String key : new String[] { "ApeironInputLinked", "ApeironInputDimension", "ApeironInputX",
            "ApeironInputY", "ApeironInputZ" }) tag.removeTag(key);
        if (linked) writeLink(tag);
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        detach();
        linked = tag.getBoolean("ApeironInputLinked");
        dimension = tag.getInteger("ApeironInputDimension");
        sourceX = tag.getInteger("ApeironInputX");
        sourceY = tag.getInteger("ApeironInputY");
        sourceZ = tag.getInteger("ApeironInputZ");
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        MTEInfinitePatternInputAssembly source = getInputSource();
        if (source == null) {
            // Client-side remote chunks may be absent. Slot and quantity synchronization populate this view only.
            if (uiPreview == null) {
                ApeironMachineTile tile = new ApeironMachineTile();
                tile.setInitialValuesAsNBT(
                    null,
                    (short) ApeironConfig.getMachineId(ApeironMachines.PATTERN_INPUT_ASSEMBLY_OFFSET));
                uiPreview = (MTEInfinitePatternInputAssembly) tile.getMetaTileEntity();
            }
            source = uiPreview;
        }
        final MTEInfinitePatternInputAssembly target = source;
        settings
            .canInteractWith(player -> getBaseMetaTileEntity().isUseableByPlayer(player) && getInputSource() == target);
        return new InfinitePatternInputGui(
            target,
            () -> getBaseMetaTileEntity().isClientSide() || getInputSource() == target).build(data, sync, settings);
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        MTEInfinitePatternInputAssembly source = getInputSource();
        if (source != null) source.writeBufferStatus(tag);
    }

    @Override
    public void getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(stack, tip, accessor, config);
        if (accessor.getNBTData()
            .hasKey("ApeironBufferStatus"))
            MTEInfinitePatternInputAssembly.addBufferStatusTooltip(tip, accessor.getNBTData());
    }
}
