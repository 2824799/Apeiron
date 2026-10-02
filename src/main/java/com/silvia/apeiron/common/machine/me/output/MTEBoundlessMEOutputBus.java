// SPDX-License-Identifier: LGPL-3.0-only
// Ported from GT5-Unofficial 5.09.54.190 (GT New Horizons contributors); modified by Apeiron.
package com.silvia.apeiron.common.machine.me.output;

import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_ME_HATCH;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_ME_HATCH_ACTIVE;

import java.math.BigInteger;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.glodblock.github.common.item.ItemFluidVoidStorageCell;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigMEInventories;
import com.silvia.apeiron.api.machine.me.output.BigItemOutputTransaction;
import com.silvia.apeiron.client.gui.machine.me.output.BoundlessMEOutputBusGui;
import com.silvia.apeiron.common.machine.me.output.storage.BigCacheCounter;
import com.silvia.apeiron.common.machine.me.output.storage.BigMEOutputProvider;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.ICellContainer;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.helpers.IPriorityHost;
import appeng.items.storage.ItemBasicStorageCell;
import appeng.items.storage.ItemVoidStorageCell;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.util.item.AEItemStack;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.OutputBusType;
import gregtech.api.interfaces.IMEConnectable;
import gregtech.api.interfaces.IOutputBus;
import gregtech.api.interfaces.IOutputBusTransaction;
import gregtech.api.interfaces.IOutputTransaction;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTSplit;
import gregtech.api.util.GTUtility;
import io.netty.buffer.ByteBuf;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

@IMetaTileEntity.SkipGenerateDescription
public class MTEBoundlessMEOutputBus extends MTEHatchOutputBus implements IPowerChannelState, IMEConnectable,
    ICellContainer, IGridProxyable, IPriorityHost, BigMEOutputProvider.Environment<IAEItemStack> {

    public MTEBoundlessMEOutputBus(int aID, String aName, String aNameRegional) {
        this(aID, aName, aNameRegional, 1);
    }

    protected MTEBoundlessMEOutputBus(int aID, String aName, String aNameRegional, int slots) {
        super(aID, aName, aNameRegional, 4, null, slots);
    }

    public MTEBoundlessMEOutputBus(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        this(aName, aTier, aDescription, aTextures, 1);
    }

    protected MTEBoundlessMEOutputBus(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures,
        int slots) {
        super(aName, aTier, slots, aDescription, aTextures);
    }

    @Override
    public ItemStack getStackForm(final long amount) {
        return new ItemStack(
            ApeironMachines.block,
            (int) Math.min(Integer.MAX_VALUE, amount),
            this.getBaseMetaTileEntity()
                .getMetaTileID());
    }

    @Override
    public String[] getDescription() {
        return GTSplit.splitLocalized("apeiron.machine.boundless_me_output_bus.desc");
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEBoundlessMEOutputBus(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_ME_HATCH_ACTIVE) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_ME_HATCH) };
    }

    EntityPlayer lastClickedPlayer = null;

    private final BigMEOutputProvider<IAEItemStack> provider = new BigMEOutputProvider<IAEItemStack>(this) {};

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        getProxy().onReady();
        provider.updateState();
    }

    @Override
    public ItemStack getVisual() {
        return this.getStackForm(1L);
    }

    @Override
    public boolean storePartial(ItemStack stack, boolean simulate) {
        IAEItemStack input = AEItemStack.create(stack);
        provider.storePartial(input, simulate);
        stack.stackSize = (int) input.getStackSize();
        return stack.stackSize == 0;
    }

    public boolean storePartialBig(final IAEItemStack input, final boolean simulate) {
        return provider.storePartialBig(input, simulate);
    }

    public BigInteger storeAmountBig(final ItemStack type, final BigInteger amount, final boolean simulate) {
        final IAEItemStack input = BigAEStackValues.copyWithSize(AEItemStack.create(type), amount);
        provider.storePartialBig(input, simulate);
        return amount.subtract(BigAEStackValues.get(input));
    }

    @Override
    public boolean isFiltered() {
        return provider.isFiltered();
    }

    @Override
    public boolean isFilteredToItem(GTUtility.ItemId id) {
        return provider.canStore(AEItemStack.create(id.getItemStack()));
    }

    @Override
    public OutputBusType getBusType() {
        if (provider.getCacheMode())
            return provider.isFiltered() ? OutputBusType.MECacheFiltered : OutputBusType.MECacheUnfiltered;
        else return provider.isFiltered() ? OutputBusType.MEFiltered : OutputBusType.MEUnfiltered;
    }

    @Override
    public IOutputBusTransaction createTransaction() {
        return new MEOutputBusTransaction();
    }

    public BigItemOutputTransaction createTransactionBig() {
        return new MEOutputBusTransaction();
    }

    @Override
    public IGridProxyable getIGridProxyable() {
        return this;
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.ITEMS;
    }

    @Override
    public ItemStack getCellStack() {
        return mInventory[0];
    }

    @Override
    public ISaveProvider getISaveProvider() {
        return this;
    }

    BaseActionSource requestSource;

    @Override
    public BaseActionSource getActionSource() {
        if (requestSource == null) requestSource = new MachineSource(this);
        return requestSource;
    }

    @Override
    public EntityPlayer getLastClickedPlayer() {
        return lastClickedPlayer;
    }

    @Override
    public IMEInventory<IAEItemStack> getNetworkInvtory() throws GridAccessException {
        return getProxy().getStorage()
            .getItemInventory();
    }

    class MEOutputBusTransaction implements BigItemOutputTransaction, IOutputTransaction.IRecipeCheckAware,
        IOutputTransaction.IProtectOutputAware {

        private final BigCacheCounter<IAEItemStack> cache = new BigCacheCounter<>();
        private boolean active = true;

        @Override
        public void setRecipeCheck(boolean isRecipeCheck) {
            // Preflight stages exact amounts in this transaction, without modifying the cell or live buffer.
        }

        @Override
        public void setProtectOutput(boolean isProtectOutput) {
            // The local buffer can accept any positive amount that passes its partition filter.
        }

        @Override
        public boolean needsTotalParallelData() {
            return false;
        }

        @Override
        public IOutputBus getBus() {
            return MTEBoundlessMEOutputBus.this;
        }

        @Override
        public boolean hasAvailableSpace() {
            return true;
        }

        @Override
        public boolean storePartial(GTUtility.ItemId id, @NotNull ItemStack stack, long totalPerParallel,
            long perParallel) {
            final IAEItemStack input = AEItemStack.create(stack);
            final BigInteger before = BigAEStackValues.get(input);
            storePartialBig(input, BigInteger.valueOf(totalPerParallel), BigInteger.valueOf(perParallel));
            final int inserted = before.subtract(BigAEStackValues.get(input))
                .intValueExact();
            stack.stackSize -= inserted;
            return inserted > 0;
        }

        @Override
        public boolean storePartialBig(final IAEItemStack input, final BigInteger totalPerParallel,
            final BigInteger perParallel) {
            if (!active) throw new IllegalStateException("Cannot add to a transaction after committing it");
            final BigInteger requested = BigAEStackValues.get(input);
            if (requested.signum() < 0) throw new IllegalArgumentException("Negative output amount");
            if (requested.signum() == 0) return false;
            if (!provider.canStore(input)) return false;
            cache.insertBig(BigAEStackValues.copyWithSize(input, BigInteger.ZERO), requested);
            BigAEStackValues.set(input, BigInteger.ZERO);
            return true;
        }

        @Override
        public void complete(GTUtility.ItemId id) {
            // Do nothing
        }

        @Override
        public void commit() {
            if (!active) throw new IllegalStateException("Transaction already committed");
            if (!cache.isEmpty()) {
                cache
                    .iterateAllBig((id, amount) -> { provider.addToCache(BigAEStackValues.copyWithSize(id, amount)); });
                provider.updateLastInputTick();
                MTEBoundlessMEOutputBus.this.markDirty();
            }
            active = false;
        }
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection forgeDirection) {
        return isOutputFacing(forgeDirection) ? AECableType.SMART : AECableType.NONE;
    }

    @Override
    public void onFacingChange() {
        provider.updateValidGridProxySides();
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        this.lastClickedPlayer = aPlayer;
        openGui(aPlayer);
        return true;
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        provider.onScrewdriverRightClick(side, aPlayer, aX, aY, aZ, aTool);
    }

    @Override
    public boolean onWireCutterRightClick(ForgeDirection side, ForgeDirection wrenchingSide, EntityPlayer aPlayer,
        float aX, float aY, float aZ, ItemStack aTool) {
        return provider.onWireCutterRightClick(side, wrenchingSide, aPlayer, aX, aY, aZ, aTool);
    }

    @Override
    public boolean connectsToAllSides() {
        return provider.getAdditionalConnection();
    }

    @Override
    public void setConnectsToAllSides(boolean connects) {
        provider.setAdditionalConnection(connects);
    }

    @Override
    public AENetworkProxy getProxy() {
        return provider.getProxy();
    }

    @Override
    public boolean isPowered() {
        return getProxy() != null && getProxy().isPowered();
    }

    @Override
    public boolean isActive() {
        return getProxy() != null && getProxy().isActive();
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return aIndex == 0 && side == aBaseMetaTileEntity.getFrontFacing() && isItemValidForSlot(aIndex, aStack);
    }

    @Override
    public boolean pushOutputInventory() {
        return false;
    }

    public boolean getCheckMode() {
        return provider.getCheckMode();
    }

    public boolean shouldCheckCell() {
        return provider.shouldCheckCell();
    }

    public boolean hasPhysicalSpace() {
        return provider.hasPhysicalSpace();
    }

    public boolean hasAvailableSpace() {
        return provider.hasAvailableSpace();
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        provider.onPostTick(aBaseMetaTileEntity, aTick);
        super.onPostTick(aBaseMetaTileEntity, aTick);
    }

    @Override
    public void notifyOutputSpaceChanged() {
        // The provider detected its free space grew or its cell was swapped/repartitioned; re-check a blocked recipe.
        notifyWatchers();
    }

    @Override
    public void onColorChangeServer(byte aColor) {
        provider.updateAE2ProxyColor();
    }

    @Override
    public boolean isLocked() {
        return provider.isFiltered();
    }

    @Override
    public void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {
        provider.addAdditionalTooltipInformation(stack, tooltip);
    }

    @Override
    public boolean shouldDropItemAt(int slot) {
        return false;
    }

    @Override
    public void setItemNBT(NBTTagCompound aNBT) {
        super.setItemNBT(aNBT);
        provider.setItemNBT(aNBT);
        com.silvia.apeiron.common.machine.block.MachineItemInventory.write(this, aNBT);
    }

    @Override
    public NBTTagCompound saveStackToNBT(IAEItemStack s) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag(
            "itemStack",
            GTUtility.saveItem(
                BigAEStackValues.copyWithSize(s, BigInteger.ONE)
                    .getItemStack()));
        BigValueCodec.writeNBT(tag, "size", "ApeironSize", new AdaptiveInteger(BigAEStackValues.get(s)));
        return tag;
    }

    @Override
    public IAEItemStack loadStackFromNBT(NBTTagCompound t) {
        final ItemStack is = GTUtility.loadItem(t.getCompoundTag("itemStack"));
        if (is == null) return null;
        return BigAEStackValues.copyWithSize(
            AEItemStack.create(is),
            BigValueCodec.readNBT(t, "size", "ApeironSize")
                .toBigInteger());
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        provider.saveNBTData(aNBT);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        com.silvia.apeiron.common.machine.block.MachineItemInventory.read(this, aNBT);
        super.loadNBTData(aNBT);
        provider.loadNBTData(aNBT);
    }

    public static final String COPIED_DATA_IDENTIFIER = "apeiron.boundlessMEOutputBus";

    @Override
    public String getCopiedDataIdentifier(EntityPlayer player) {
        return COPIED_DATA_IDENTIFIER;
    }

    @Override
    public NBTTagCompound getCopiedData(EntityPlayer player) {
        return provider.getCopiedData(player);
    }

    @Override
    public boolean pasteCopiedData(EntityPlayer player, NBTTagCompound nbt) {
        return provider.pasteCopiedData(player, nbt);
    }

    @Override
    public void writeToStream(ByteBuf buffer) {
        super.writeToStream(buffer);

        // Synchronize the unlimited-buffer marker for client previews.

        provider.writeToClientPacket(buffer);
    }

    @Override
    public void readFromStream(ByteBuf buffer) {
        super.readFromStream(buffer);
        provider.readFromClientPacket(buffer);
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        provider.getWailaNBTData(player, tile, tag, world, x, y, z);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getWailaBody(ItemStack itemStack, List<String> ss, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(itemStack, ss, accessor, config);

        ss.add(net.minecraft.util.StatCollector.translateToLocal("apeiron.machine.me_output.unlimited_cache"));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasWailaAdvancedBody(ItemStack itemStack, IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getWailaAdvancedBody(ItemStack itemStack, List<String> ss, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaAdvancedBody(itemStack, ss, accessor, config);
        BigMEOutputProvider.WailaHelper.getWailaAdvancedBody("item", ss, accessor);
    }

    @Override
    public String[] getInfoData() {
        return provider.getInfoData(
            getAEDiagnostics(),
            "GT5U.infodata.hatch.output_bus_me",
            (IAEItemStack s) -> s.getItem()
                .getItemStackDisplayName(s.getItemStack()));
    }

    @Override
    public boolean acceptsConfigCopy() {
        return false;
    }

    @Override
    public void onContentsChanged(int slot) {
        provider.onContentsChanged(slot);
    }

    @MENetworkEventSubscribe
    public void updateCell(final MENetworkChannelsChanged c) {
        provider.updateCell();
    }

    @Override
    public List<IMEInventoryHandler> getCellArray(final StorageChannel channel) {
        return provider.getCellArray(channel);
    }

    @Override
    public int getPriority() {
        return provider.getPriority();
    }

    @Override
    public void setPriority(int newValue) {
        provider.setPriority(newValue);
    }

    @Override
    public void saveChanges(IMEInventory cellInventory) {
        markDirty();
    }

    @Override
    public IGridNode getActionableNode() {
        return getProxy().getNode();
    }

    @Override
    public DimensionalCoord getLocation() {
        IGregTechTileEntity gtm = this.getBaseMetaTileEntity();
        return new DimensionalCoord(gtm.getWorld(), gtm.getXCoord(), gtm.getYCoord(), gtm.getZCoord());
    }

    @Override
    public void securityBreak() {}

    @Override
    public IGridNode getGridNode(ForgeDirection forgeDirection) {
        return getProxy().getNode();
    }

    @Override
    public void dispatchMarkDirty() {
        this.markDirty();
    }

    @Override
    public BigMEOutputProvider<IAEItemStack> getProvider() {
        return provider;
    }

    @Override
    public String getEnableKey() {
        return "GT5U.hatch.item.filter.enable";
    }

    @Override
    public String getDisableKey() {
        return "GT5U.hatch.item.filter.disable";
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        return new BoundlessMEOutputBusGui(this).build(guiData, syncManager, uiSettings);
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack itemStack) {
        return itemStack != null && isItemCell(itemStack) && super.isItemValidForSlot(index, itemStack);
    }

    private boolean isItemCell(@NotNull ItemStack itemStack) {
        Item item = itemStack.getItem();
        return item instanceof ItemBasicStorageCell
            || BigMEInventories.isUnlimitedCell(itemStack, appeng.util.item.AEItemStackType.ITEM_STACK_TYPE)
            || item instanceof ItemVoidStorageCell && !(item instanceof ItemFluidVoidStorageCell);
    }
}
