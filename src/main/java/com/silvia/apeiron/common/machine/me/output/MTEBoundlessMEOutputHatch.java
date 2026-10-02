// SPDX-License-Identifier: LGPL-3.0-only
// Ported from GT5-Unofficial 5.09.54.190 (GT New Horizons contributors); modified by Apeiron.
package com.silvia.apeiron.common.machine.me.output;

import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_ME_FLUID_HATCH;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_ME_FLUID_HATCH_ACTIVE;

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
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.glodblock.github.common.item.FCBaseItemCell;
import com.glodblock.github.common.item.ItemFluidVoidStorageCell;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigMEInventories;
import com.silvia.apeiron.api.machine.me.output.BigFluidOutputTransaction;
import com.silvia.apeiron.client.gui.machine.me.output.BoundlessMEOutputHatchGui;
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
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.helpers.IPriorityHost;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.util.item.AEFluidStack;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.OutputHatchType;
import gregtech.api.interfaces.IDataCopyable;
import gregtech.api.interfaces.IMEConnectable;
import gregtech.api.interfaces.IOutputHatch;
import gregtech.api.interfaces.IOutputHatchTransaction;
import gregtech.api.interfaces.IOutputTransaction;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTSplit;
import gregtech.api.util.GTUtility;
import io.netty.buffer.ByteBuf;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

@IMetaTileEntity.SkipGenerateDescription
public class MTEBoundlessMEOutputHatch extends MTEHatchOutput implements IPowerChannelState, IMEConnectable,
    IDataCopyable, ICellContainer, IGridProxyable, IPriorityHost, BigMEOutputProvider.Environment<IAEFluidStack> {

    public MTEBoundlessMEOutputHatch(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 4, null, 1);
    }

    private final BigMEOutputProvider<IAEFluidStack> provider = createProvider();

    protected BigMEOutputProvider<IAEFluidStack> createProvider() {
        return new BigMEOutputProvider<IAEFluidStack>(this) {};
    }

    public MTEBoundlessMEOutputHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 1, aDescription, aTextures);
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
        return GTSplit.splitLocalized("apeiron.machine.boundless_me_output_hatch.desc");
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEBoundlessMEOutputHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_ME_FLUID_HATCH_ACTIVE) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_ME_FLUID_HATCH) };
    }

    @Override
    public byte getTierForStructure() {
        return (byte) (GTValues.V.length - 2);
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        getProxy().onReady();
        provider.updateState();
    }

    @MENetworkEventSubscribe
    public void updateCell(final MENetworkChannelsChanged c) {
        provider.updateCell();
    }

    @Override
    public int fill(FluidStack aFluid, boolean doFill) {
        IAEFluidStack input = AEFluidStack.create(aFluid);
        provider.storePartial(input, !doFill);
        return aFluid.amount - (int) input.getStackSize();
    }

    public BigInteger fillBig(final FluidStack type, final BigInteger amount, final boolean doFill) {
        final IAEFluidStack input = BigAEStackValues.copyWithSize(AEFluidStack.create(type), amount);
        provider.storePartialBig(input, !doFill);
        return amount.subtract(BigAEStackValues.get(input));
    }

    public boolean storePartialBig(final IAEFluidStack input, final boolean simulate) {
        return provider.storePartialBig(input, simulate);
    }

    @Override
    public boolean canStoreFluid(@NotNull FluidStack fluidStack) {
        return provider.canStore(AEFluidStack.create(fluidStack));
    }

    @Override
    public boolean isFluidLocked() {
        return provider.isFiltered();
    }

    @Override
    public int getCapacity() {
        return 0;
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
    public boolean isEmptyAndAcceptsAnyFluid() {
        return !provider.isFiltered() && !provider.getCheckMode();
    }

    BaseActionSource requestSource;

    @Override
    public BaseActionSource getActionSource() {
        if (requestSource == null) requestSource = new MachineSource(this);
        return requestSource;
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection forgeDirection) {
        return isOutputFacing(forgeDirection) ? AECableType.SMART : AECableType.NONE;
    }

    @Override
    public void onFacingChange() {
        provider.updateValidGridProxySides();
    }

    EntityPlayer lastClickedPlayer = null;

    @Override
    public boolean acceptsConfigCopy() {
        return false;
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        lastClickedPlayer = aPlayer;

        openGui(aPlayer);

        return true;
    }

    @Override
    public void onColorChangeServer(byte aColor) {
        provider.updateAE2ProxyColor();
    }

    @Override
    public boolean isValidSlot(int aIndex) {
        return true;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return aIndex == 0 && side == aBaseMetaTileEntity.getFrontFacing() && isItemValidForSlot(aIndex, aStack);
    }

    @Override
    public boolean doesFillContainers() {
        return false;
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
    public void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {
        provider.addAdditionalTooltipInformation(stack, tooltip);
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
        BigMEOutputProvider.WailaHelper.getWailaAdvancedBody("fluid", ss, accessor);
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

    @Override
    public IGridProxyable getIGridProxyable() {
        return this;
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.FLUIDS;
    }

    @Override
    public ItemStack getCellStack() {
        return mInventory[0];
    }

    @Override
    public ISaveProvider getISaveProvider() {
        return this;
    }

    @Override
    public EntityPlayer getLastClickedPlayer() {
        return lastClickedPlayer;
    }

    @Override
    public IMEInventory<IAEFluidStack> getNetworkInvtory() throws GridAccessException {
        return getProxy().getStorage()
            .getFluidInventory();
    }

    @Override
    public NBTTagCompound saveStackToNBT(IAEFluidStack s) {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound tagFluidStack = new NBTTagCompound();
        BigAEStackValues.copyWithSize(s, BigInteger.ONE)
            .getFluidStack()
            .writeToNBT(tagFluidStack);
        tag.setTag("fluidStack", tagFluidStack);
        BigValueCodec.writeNBT(tag, "size", "ApeironSize", new AdaptiveInteger(BigAEStackValues.get(s)));
        return tag;
    }

    @Override
    public IAEFluidStack loadStackFromNBT(NBTTagCompound tag) {
        final FluidStack fluid = GTUtility.loadFluid(tag.getCompoundTag("fluidStack"));
        if (fluid == null) return null;
        return BigAEStackValues.copyWithSize(
            AEFluidStack.create(fluid),
            BigValueCodec.readNBT(tag, "size", "ApeironSize")
                .toBigInteger());
    }

    public static final String COPIED_DATA_IDENTIFIER = "apeiron.boundlessMEOutputHatch";

    @Override
    public String getCopiedDataIdentifier(EntityPlayer player) {
        return COPIED_DATA_IDENTIFIER;
    }

    @Override
    public ItemStack getVisual() {
        return this.getStackForm(1L);
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
    public String[] getInfoData() {
        return provider.getInfoData(
            getAEDiagnostics(),
            "GT5U.infodata.hatch.output_me",
            (IAEFluidStack s) -> s.getFluidStack()
                .getLocalizedName());
    }

    @Override
    public void onContentsChanged(int slot) {
        provider.onContentsChanged(slot);
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
    public BigMEOutputProvider<IAEFluidStack> getProvider() {
        return provider;
    }

    @Override
    public String getEnableKey() {
        return "GT5U.hatch.fluid.filter.enable";
    }

    @Override
    public String getDisableKey() {
        return "GT5U.hatch.fluid.filter.disable";
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        return new BoundlessMEOutputHatchGui(this).build(guiData, syncManager, uiSettings);
    }

    @Override
    public boolean isFiltered() {
        return provider.isFiltered();
    }

    @Override
    public boolean isFilteredToFluid(GTUtility.FluidId id) {
        return canStoreFluid(id.getFluidStack());
    }

    @Override
    public OutputHatchType getHatchType() {
        if (provider.getCacheMode())
            return provider.isFiltered() ? OutputHatchType.MECacheFiltered : OutputHatchType.MECacheUnfiltered;
        else return provider.isFiltered() ? OutputHatchType.MEFiltered : OutputHatchType.MEUnfiltered;
    }

    @Override
    public IOutputHatchTransaction createTransaction() {
        return new MEOutputHatchTransaction();
    }

    public BigFluidOutputTransaction createTransactionBig() {
        return new MEOutputHatchTransaction();
    }

    class MEOutputHatchTransaction implements BigFluidOutputTransaction, IOutputTransaction.IRecipeCheckAware,
        IOutputTransaction.IProtectOutputAware {

        private final BigCacheCounter<IAEFluidStack> cache = new BigCacheCounter<>();
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
        public IOutputHatch getHatch() {
            return MTEBoundlessMEOutputHatch.this;
        }

        @Override
        public boolean hasAvailableSpace() {
            return true;
        }

        @Override
        public boolean storePartial(GTUtility.FluidId id, @NotNull FluidStack stack, long totalPerParallel,
            long perParallel) {
            final IAEFluidStack input = AEFluidStack.create(stack);
            final BigInteger before = BigAEStackValues.get(input);
            storePartialBig(input, BigInteger.valueOf(totalPerParallel), BigInteger.valueOf(perParallel));
            final int inserted = before.subtract(BigAEStackValues.get(input))
                .intValueExact();
            stack.amount -= inserted;
            return inserted > 0;
        }

        @Override
        public boolean storePartialBig(final IAEFluidStack input, final BigInteger totalPerParallel,
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
        public void complete(GTUtility.FluidId id) {
            // Do nothing
        }

        @Override
        public void commit() {
            if (!active) throw new IllegalStateException("Transaction already committed");
            if (!cache.isEmpty()) {
                cache
                    .iterateAllBig((id, amount) -> { provider.addToCache(BigAEStackValues.copyWithSize(id, amount)); });
                provider.updateLastInputTick();
                MTEBoundlessMEOutputHatch.this.markDirty();
            }
            active = false;
        }
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack itemStack) {
        return itemStack != null && isFluidCell(itemStack) && super.isItemValidForSlot(index, itemStack);
    }

    private boolean isFluidCell(@NotNull ItemStack itemStack) {
        Item item = itemStack.getItem();
        return item instanceof FCBaseItemCell || item instanceof ItemFluidVoidStorageCell
            || BigMEInventories.isUnlimitedCell(itemStack, appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE);
    }
}
