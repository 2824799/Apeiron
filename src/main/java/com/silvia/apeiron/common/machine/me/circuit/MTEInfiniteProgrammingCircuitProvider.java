// SPDX-License-Identifier: MIT
// Circuit wrapping, prefab interoperability and ME host lifecycle adapted from Programmable Hatches (c) 2024 reobf.
package com.silvia.apeiron.common.machine.me.circuit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.stack.InfiniteAEStack;
import com.silvia.apeiron.ae.storage.BigMEInventory;
import com.silvia.apeiron.client.gui.machine.me.input.InfiniteCircuitProviderGui;
import com.silvia.apeiron.common.integration.proghatches.ProgrammingCircuitTemplates;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;

import appeng.api.config.Actionable;
import appeng.api.config.PowerUnits;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.ICellContainer;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.me.storage.MEInventoryHandler;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;
import appeng.util.item.ItemList;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Optional;
import gregtech.api.enums.Textures.BlockIcons;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTSplit;
import reobf.proghatches.gt.metatileentity.util.ICircuitProvider;

@Optional.Interface(modid = "programmablehatches", iface = "reobf.proghatches.gt.metatileentity.util.ICircuitProvider")
@IMetaTileEntity.SkipGenerateDescription
public class MTEInfiniteProgrammingCircuitProvider extends MTEHatch
    implements IGridProxyable, ICellContainer, IPowerChannelState, ICircuitProvider {

    public static final int SLOT_COUNT = 81;
    public static final int EU_PER_TICK = 1024;
    private AENetworkProxy proxy;
    private ItemList circuits = new ItemList();
    private boolean dirty = true;
    private boolean circuitsChanged = true;
    private boolean wasActive;
    private boolean additionalConnection;
    private final CircuitInventory inventory = new CircuitInventory();
    private final MEInventoryHandler<IAEItemStack> handler = new MEInventoryHandler<>(
        inventory,
        AEItemStackType.ITEM_STACK_TYPE);

    public MTEInfiniteProgrammingCircuitProvider(int id, String name, String localName) {
        super(id, name, localName, 10, SLOT_COUNT, (String[]) null);
    }

    protected MTEInfiniteProgrammingCircuitProvider(String name, int tier, String[] description,
        ITexture[][][] textures) {
        super(name, tier, SLOT_COUNT, description, textures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEInfiniteProgrammingCircuitProvider(mName, mTier, mDescriptionArray, mTextures);
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
            .translateToLocal("gt.blockmachines.apeiron.infinite_circuit_provider.name");
    }

    @Override
    public String[] getDescription() {
        return GTSplit.splitLocalized("apeiron.machine.infinite_circuit_provider.desc");
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
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return slot >= 0 && slot < SLOT_COUNT && stack != null;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        return new InfiniteCircuitProviderGui(this).build(data, sync, settings);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture base) {
        if (Loader.isModLoaded("programmablehatches")) return ProgrammingCircuitTemplates.textures(base, true);
        return new ITexture[] { base, TextureFactory.of(BlockIcons.OVERLAY_ME_INPUT_HATCH_ACTIVE) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture base) {
        if (Loader.isModLoaded("programmablehatches")) return ProgrammingCircuitTemplates.textures(base, false);
        return new ITexture[] { base, TextureFactory.of(BlockIcons.OVERLAY_ME_INPUT_HATCH) };
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
    public boolean isValidSlot(int slot) {
        return false;
    }

    @Override
    public void onContentsChanged(int slot) {
        dirty = true;
        circuitsChanged = true;
        markDirty();
    }

    @Override
    public boolean shouldDropItemAt(int slot) {
        return false;
    }

    @Override
    public void clearDirty() {
        circuitsChanged = false;
    }

    @Override
    public boolean patternDirty() {
        return circuitsChanged;
    }

    public void rebuildCircuits() {
        circuits = new ItemList();
        if (Loader.isModLoaded("programmablehatches")) {
            boolean empty = false;
            for (ItemStack sample : mInventory) {
                if (sample == null && empty) continue;
                if (sample == null) empty = true;
                for (ItemStack circuit : ProgrammingCircuitTemplates.expand(sample)) if (circuit != null) {
                    IAEItemStack stack = AEItemStack.create(circuit);
                    stack.setStackSize(1);
                    if (stack instanceof InfiniteAEStack) ((InfiniteAEStack) stack).setInfinite(true);
                    circuits.addStorage(stack);
                }
            }
        }
        dirty = false;
        notifyNetwork();
    }

    @Override
    public Collection<ItemStack> getCircuit() {
        if (dirty) rebuildCircuits();
        List<ItemStack> result = new ArrayList<>();
        for (IAEItemStack stack : circuits) {
            ItemStack item = stack.getItemStack();
            item.stackSize = 1;
            result.add(item);
        }
        return result;
    }

    public int getProvidedTypeCount() {
        if (dirty) rebuildCircuits();
        return circuits.size();
    }

    @Override
    public AENetworkProxy getProxy() {
        if (proxy == null) {
            proxy = new AENetworkProxy(this, "proxy", getStackForm(1), true);
            proxy.setFlags(GridFlags.REQUIRE_CHANNEL);
            proxy.setIdlePowerUsage(PowerUnits.EU.convertTo(PowerUnits.AE, EU_PER_TICK));
            updateSides();
            if (getBaseMetaTileEntity().getWorld() != null) proxy.setOwner(
                getBaseMetaTileEntity().getWorld()
                    .getPlayerEntityByName(getBaseMetaTileEntity().getOwnerName()));
        }
        return proxy;
    }

    private void updateSides() {
        if (proxy != null) proxy.setValidSides(
            additionalConnection ? EnumSet.complementOf(EnumSet.of(ForgeDirection.UNKNOWN))
                : EnumSet.of(getBaseMetaTileEntity().getFrontFacing()));
    }

    @Override
    public void onFacingChange() {
        updateSides();
    }

    @Override
    public boolean isFacingValid(ForgeDirection side) {
        return side != ForgeDirection.UNKNOWN;
    }

    @Override
    public void onFirstTick(IGregTechTileEntity tile) {
        super.onFirstTick(tile);
        getProxy().onReady();
        rebuildCircuits();
    }

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        if (tile.isServerSide()) {
            if (dirty || tile.hasInventoryBeenModified()) rebuildCircuits();
            boolean active = isActive();
            if (active != wasActive) {
                wasActive = active;
                notifyNetwork();
            }
            tile.setActive(active);
        }
    }

    @Override
    public void onRemoval() {
        if (proxy != null) proxy.invalidate();
        super.onRemoval();
    }

    @Override
    public IGridNode getGridNode(ForgeDirection side) {
        return getProxy().getNode();
    }

    @Override
    public IGridNode getActionableNode() {
        return getProxy().getNode();
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection side) {
        return AECableType.DENSE;
    }

    @Override
    public DimensionalCoord getLocation() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        return new DimensionalCoord(tile.getWorld(), tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
    }

    @Override
    public void gridChanged() {
        notifyNetwork();
    }

    @Override
    public void securityBreak() {
        getProxy().invalidate();
    }

    @Override
    public boolean isPowered() {
        return getProxy().isPowered();
    }

    @Override
    public boolean isActive() {
        return getProxy().isActive() && getBaseMetaTileEntity().isAllowedToWork()
            && Loader.isModLoaded("programmablehatches");
    }

    @Override
    public boolean onWireCutterRightClick(ForgeDirection side, ForgeDirection wrenchingSide, EntityPlayer player,
        float x, float y, float z, ItemStack tool) {
        additionalConnection = !additionalConnection;
        updateSides();
        markDirty();
        return true;
    }

    private void notifyNetwork() {
        if (proxy == null) return;
        try {
            proxy.getGrid()
                .postEvent(new MENetworkCellArrayUpdate());
        } catch (GridAccessException ignored) {}
    }

    @MENetworkEventSubscribe
    public void powerChanged(MENetworkPowerStatusChange event) {
        notifyNetwork();
    }

    @MENetworkEventSubscribe
    public void channelsChanged(MENetworkChannelsChanged event) {
        notifyNetwork();
    }

    @Override
    public List<IMEInventoryHandler> getCellArray(StorageChannel channel) {
        if (dirty) rebuildCircuits();
        return channel == StorageChannel.ITEMS && isActive() ? Collections.singletonList(handler)
            : Collections.emptyList();
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public void saveChanges(IMEInventory cell) {
        markDirty();
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        tag.setBoolean("ApeironAllSides", additionalConnection);
        if (proxy != null) proxy.writeToNBT(tag);
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        additionalConnection = tag.getBoolean("ApeironAllSides");
        getProxy().readFromNBT(tag);
        updateSides();
        dirty = true;
    }

    public final class CircuitInventory implements IMEInventory<IAEItemStack>, BigMEInventory {

        @Override
        public IAEItemStack injectItems(IAEItemStack input, Actionable mode, BaseActionSource source) {
            return isActive() && circuits.findPrecise(input) != null ? null : input;
        }

        @Override
        public IAEItemStack extractItems(IAEItemStack request, Actionable mode, BaseActionSource source) {
            if (dirty) rebuildCircuits();
            if (!isActive() || request == null
                || BigAEStackValues.get(request)
                    .signum() <= 0
                || circuits.findPrecise(request) == null) return null;
            IAEItemStack result = request.copy();
            if (result instanceof InfiniteAEStack) ((InfiniteAEStack) result).setInfinite(false);
            BigAEStackValues.set(result, BigAEStackValues.get(request));
            return result;
        }

        @Override
        public IAEStack<?> injectItemsBig(IAEStack<?> input, Actionable mode, BaseActionSource source) {
            return input instanceof IAEItemStack ? injectItems((IAEItemStack) input, mode, source) : input;
        }

        @Override
        public IAEStack<?> extractItemsBig(IAEStack<?> request, Actionable mode, BaseActionSource source) {
            return request instanceof IAEItemStack ? extractItems((IAEItemStack) request, mode, source) : null;
        }

        @Override
        public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out, int iteration) {
            if (dirty) rebuildCircuits();
            if (isActive()) for (IAEItemStack stack : circuits) out.addStorage(stack.copy());
            return out;
        }

        @Override
        public StorageChannel getChannel() {
            return StorageChannel.ITEMS;
        }
    }

    public CircuitInventory getCircuitInventory() {
        return inventory;
    }
}
