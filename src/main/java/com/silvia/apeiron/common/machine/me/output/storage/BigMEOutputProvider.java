// SPDX-License-Identifier: LGPL-3.0-only
// Ported from GT5-Unofficial 5.09.54.190 (GT New Horizons contributors); modified by Apeiron.
package com.silvia.apeiron.common.machine.me.output.storage;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.getFluidUnit;
import static gregtech.common.covers.modes.FilterType.BLACKLIST;
import static gregtech.common.covers.modes.FilterType.WHITELIST;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import com.silvia.apeiron.ae.automation.BigPoweredTransfers;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigCellInventory;
import com.silvia.apeiron.ae.storage.BigMEInventories;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigNumberFormatter;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.IncludeExclude;
import appeng.api.networking.GridFlags;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.MEMonitorHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.AEColor;
import appeng.items.AEBaseCell;
import appeng.items.contents.CellConfig;
import appeng.items.storage.ItemVoidStorageCell;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.me.storage.CellInventory;
import appeng.me.storage.CellInventoryHandler;
import appeng.me.storage.MEInventoryHandler;
import appeng.me.storage.VoidCellInventory;
import appeng.util.IterationCounter;
import appeng.util.Platform;
import appeng.util.prioitylist.OreFilteredList;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.Dyes;
import gregtech.api.interfaces.tileentity.IGregTechDeviceInformation;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;
import io.netty.buffer.ByteBuf;
import mcp.mobius.waila.api.IWailaDataAccessor;

public abstract class BigMEOutputProvider<T extends IAEStack<T>> {

    public interface Environment<T extends IAEStack<T>> {

        @Nullable
        IGregTechTileEntity getBaseMetaTileEntity();

        IGridProxyable getIGridProxyable();

        StorageChannel getChannel();

        ItemStack getCellStack();

        ISaveProvider getISaveProvider();

        BaseActionSource getActionSource();

        EntityPlayer getLastClickedPlayer();

        IMEInventory<T> getNetworkInvtory() throws GridAccessException;

        NBTTagCompound saveStackToNBT(T stack);

        T loadStackFromNBT(NBTTagCompound tag);

        byte getColor();

        String getCopiedDataIdentifier(EntityPlayer player);

        ItemStack getVisual();

        void dispatchMarkDirty();

        /**
         * Called when what this output can accept changed - free space grew, or the storage cell was swapped or
         * repartitioned - so a recipe blocked on output-full can be re-checked.
         */
        void notifyOutputSpaceChanged();

        BigMEOutputProvider<T> getProvider();

        String getEnableKey();

        String getDisableKey();
    }

    private final Environment<T> env;
    protected AENetworkProxy proxy;
    protected final BigCacheCounter<T> cache = new BigCacheCounter<>();

    public BigMEOutputProvider(Environment<T> env) {
        this.env = env;
        updateState();
    }

    /** The local output buffer has no numeric limit, including with a finite storage cell installed. */
    public boolean isCacheUnlimited() {
        return true;
    }

    public AENetworkProxy getProxy() {
        if (proxy == null) {
            proxy = new AENetworkProxy(env.getIGridProxyable(), "proxy", env.getVisual(), true);
            proxy.setFlags(GridFlags.REQUIRE_CHANNEL);
            proxy.setValidSides(EnumSet.noneOf(ForgeDirection.class));
            updateValidGridProxySides();
        }
        return proxy;
    }

    private boolean wasActive = false;

    public void updateCell() {
        final boolean currentActive = getProxy().isActive();
        if (this.wasActive != currentActive) {
            this.wasActive = currentActive;
            this.updateCellArray();
        }
    }

    public List<IMEInventoryHandler> getCellArray(final StorageChannel channel) {
        if (cacheMode && this.getProxy()
            .isActive() && channel == env.getChannel()) {
            if (cellRead != null) return Collections.singletonList(cellRead);
        }
        return Collections.emptyList();
    }

    public int getPriority() {
        return myPriority;
    }

    public void setPriority(int newValue) {
        myPriority = newValue;
        isCached = false;
        updateState();
        updateCellArray();
        env.dispatchMarkDirty();
    }

    @Nullable
    OutputMonitorHandler<T> cell;
    @Nullable
    OutputMonitorHandler<T> cellRead;
    @Nullable
    CellInventoryHandler<T> handler;
    private ItemStack oldCellStack = null;
    private int myPriority = 0;

    boolean cacheMode = false;
    boolean isCached = false;

    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        setCacheMode(!cacheMode);
        updateState();
        cellToCacheTransfer();
        this.updateCellArray();
        env.dispatchMarkDirty();
    }

    boolean additionalConnection = false;

    public boolean onWireCutterRightClick(ForgeDirection side, ForgeDirection wrenchingSide, EntityPlayer aPlayer,
        float aX, float aY, float aZ, ItemStack aTool) {
        setAdditionalConnection(!additionalConnection);
        aPlayer.addChatComponentMessage(
            new ChatComponentTranslation("GT5U.hatch.additionalConnection." + additionalConnection));
        return true;
    }

    public void updateValidGridProxySides() {
        if (additionalConnection) {
            getProxy().setValidSides(EnumSet.complementOf(EnumSet.of(ForgeDirection.UNKNOWN)));
        } else {
            getProxy().setValidSides(
                EnumSet.of(
                    env.getBaseMetaTileEntity()
                        .getFrontFacing()));
        }
    }

    public boolean getAdditionalConnection() {
        return additionalConnection;
    }

    public void setAdditionalConnection(boolean connects) {
        additionalConnection = connects;
        updateValidGridProxySides();
        env.dispatchMarkDirty();
    }

    public void updateState() {
        if (this.isCached) {
            return;
        }
        this.isCached = true;
        this.cell = null;
        this.cellRead = null;
        this.handler = null;
        final ItemStack is = env.getCellStack();
        if (is != null) {
            final IMEInventoryHandler<T> cell = AEApi.instance()
                .registries()
                .cell()
                .getCellInventory(is, env.getISaveProvider(), env.getChannel());
            if (cell != null) {
                this.cell = this.wrap(cell, AccessRestriction.READ_WRITE);
                this.cellRead = this.wrap(cell, AccessRestriction.READ);
                if (this.cell != null) this.handler = this.cell.getCellInventoryHandler();
                env.dispatchMarkDirty();
            }
        }
        isVoidCell = is != null && is.getItem() instanceof ItemVoidStorageCell;
    }

    private static class OutputMonitorHandler<T extends IAEStack<T>> extends MEMonitorHandler<T> {

        public OutputMonitorHandler(final IMEInventoryHandler<T> t) {
            super(t);
        }

        public @Nullable CellInventoryHandler<T> getCellInventoryHandler() {
            var inv = getHandler().getInternal();
            if (inv instanceof CellInventoryHandler ci) return (CellInventoryHandler<T>) ci;
            return null;
        }

        public boolean hasUnlimitedCapacity() {
            return BigMEInventories.hasUnlimitedCapacity(getHandler());
        }
    }

    private <StackType extends IAEStack<StackType>> OutputMonitorHandler<StackType> wrap(
        final IMEInventoryHandler<StackType> h, final AccessRestriction myAccess) {
        if (h == null) {
            return null;
        }

        final MEInventoryHandler<StackType> ih = new MEInventoryHandler<>(
            h,
            (IAEStackType<StackType>) h.getStackType());
        ih.setPriority(this.myPriority);
        ih.setBaseAccess(myAccess);

        final OutputMonitorHandler<StackType> g = new OutputMonitorHandler<>(ih);
        g.addListener(new OutputNetNotifier(h.getChannel()), g);

        return g;
    }

    private class OutputNetNotifier implements IMEMonitorHandlerReceiver<IAEStack<?>> {

        private final StorageChannel chan;

        public OutputNetNotifier(final StorageChannel chan) {
            this.chan = chan;
        }

        @Override
        public boolean isValid(final Object verificationToken) {
            if (this.chan == env.getChannel()) {
                return verificationToken == cell;
            }
            return false;
        }

        @Override
        public void postChange(final IBaseMonitor<IAEStack<?>> monitor, final Iterable<IAEStack<?>> change,
            final BaseActionSource source) {
            try {
                if (getProxy().isActive()) {
                    getProxy().getStorage()
                        .postAlterationOfStoredItems(this.chan, change, env.getActionSource());
                }
            } catch (final GridAccessException e) {
                // :(
            }
        }

        @Override
        public void onListUpdate() {
            try {
                BigMEOutputProvider.this.updateCellArray();
                final IStorageGrid gs = BigMEOutputProvider.this.getProxy()
                    .getStorage();
                Platform.postChanges(gs, null, env.getCellStack(), env.getActionSource());
            } catch (GridAccessException e) {
                // :(
            }
        }
    }

    public void onContentsChanged(int slot) {
        if (slot != 0) return;

        ItemStack upgradeItemStack = env.getCellStack();
        if (GTUtility.areStacksEqualOrNull(oldCellStack, upgradeItemStack)) {
            return;
        }

        if (upgradeItemStack != null) {
            Item item = upgradeItemStack.getItem();
            boolean isCell = item instanceof AEBaseCell || item instanceof ItemVoidStorageCell
                || BigMEInventories.isUnlimitedCell(
                    upgradeItemStack,
                    env.getChannel() == StorageChannel.ITEMS ? appeng.util.item.AEItemStackType.ITEM_STACK_TYPE
                        : appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE);
            if (!isCell) return;
        }

        if (this.isCached) {
            this.isCached = false;
            updateState();
        }
        sendFilterMessage();

        if (cacheMode) {
            try {
                this.updateCellArray();
                final IStorageGrid gs = this.getProxy()
                    .getStorage();
                Platform.postChanges(gs, oldCellStack, upgradeItemStack, env.getActionSource());
            } catch (final GridAccessException ignored) {}
        }

        oldCellStack = upgradeItemStack;
        // A swapped or repartitioned cell changes what this output accepts, which can unblock a recipe.
        env.notifyOutputSpaceChanged();
        env.dispatchMarkDirty();
    }

    private void sendFilterMessage() {
        if (env.getLastClickedPlayer() == null || !GTUtility.isServer()) {
            return;
        }
        ItemStack upgradeItemStack = env.getCellStack();
        if (upgradeItemStack == null || !(upgradeItemStack.getItem() instanceof ICellWorkbenchItem cellWorkbenchItem)
            || !isFiltered()) {
            IChatComponent msg = new ChatComponentTranslation(env.getDisableKey());
            GTUtility.sendChatComp(env.getLastClickedPlayer(), msg);
            return;
        }
        CellConfig cfg = (CellConfig) cellWorkbenchItem.getConfigAEInventory(upgradeItemStack);

        String modeKey = isWhiteList() ? WHITELIST.getKey() : BLACKLIST.getKey();
        IChatComponent msg = new ChatComponentTranslation(
            "GT5U.hatch.outputme.filter.format",
            new ChatComponentTranslation(env.getEnableKey()),
            new ChatComponentTranslation(modeKey));
        if (handler.getPartitionList() instanceof OreFilteredList) {
            ICellInventory<T> ci = handler.getCellInv();
            if (ci != null) {
                msg.appendText(ci.getOreFilter());
            }
        } else {
            for (int i = 0; i < cfg.getSizeInventory(); i++) {
                IAEStack<?> stack = cfg.getAEStackInSlot(i);
                if (stack != null) {
                    msg.appendSibling(stack.getChatComponent());
                }
            }
        }
        GTUtility.sendChatComp(env.getLastClickedPlayer(), msg);
    }

    boolean isVoidCell = false;

    long lastOutputTick = 0;
    long lastInputTick = 0;
    long tickCounter = 0;

    public final long getTickCounter() {
        return tickCounter;
    }

    public final long getLastInputTick() {
        return lastInputTick;
    }

    public final void updateLastInputTick() {
        lastInputTick = tickCounter;
    }

    public boolean hasPhysicalSpace() {
        return true;
    }

    public boolean isCellCacheUnlimited() {
        return cacheMode && cell != null && cell.hasUnlimitedCapacity();
    }

    public boolean hasAvailableSpace() {
        return hasPhysicalSpace();
    }

    public long getPhysicalSpace() {
        return Long.MAX_VALUE;
    }

    public long getCachedAmount() {
        return cache.getTotal();
    }

    public BigInteger getCachedAmountBig() {
        return cache.getTotalBig();
    }

    public long getCachedAmount(T key) {
        return cache.get(key);
    }

    public BigInteger getCachedAmountBig(T key) {
        return cache.getBig(key);
    }

    /**
     * Legacy numeric view only. Acceptance uses the unlimited flag and never compares amounts to this value.
     */
    public long getCacheCapacity() {
        return Long.MAX_VALUE;
    }

    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (aBaseMetaTileEntity.isServerSide()) {
            tickCounter = aTick;
            if (tickCounter > (lastOutputTick + 40)) flushCachedStack();
            if (tickCounter % 20 == 0) {
                updateCell();
                aBaseMetaTileEntity.setActive(wasActive);
            }
        }
    }

    public void setItemNBT(NBTTagCompound aNBT) {
        writeCacheAndSettings(aNBT);
    }

    public void flushCachedStack() {
        flushCachedStackBig();
    }

    public void flushCachedStackBig() {
        final AENetworkProxy proxy = getProxy();
        if (cache.isEmpty() || !proxy.isActive()) return;
        try {
            final IEnergySource energy = proxy.getEnergy();
            final IMEInventory<T> storage = cacheMode && cell != null ? cell : env.getNetworkInvtory();
            final BigInteger before = cache.getTotalBig();
            cache.updateAllBig((stack, amount) -> {
                final T request = BigAEStackValues.copyWithSize(stack, amount);
                final IAEStack<?> rest = BigPoweredTransfers
                    .poweredInsertBulkBig(energy, storage, request, env.getActionSource(), Actionable.MODULATE);
                return BigAEStackValues.get(rest);
            });
            if (!cache.getTotalBig()
                .equals(before)) env.dispatchMarkDirty();
        } catch (final GridAccessException ignored) {}
        lastOutputTick = tickCounter;
    }

    public void cellToCacheTransfer() {
        if (!cacheMode && cell != null) {
            final Iterable<T> iter = cell.getAvailableItems(
                cell.getStackType()
                    .createPrimitiveList(),
                IterationCounter.fetchNewId());
            iter.forEach(
                stack -> addToCache(
                    BigMEInventories.extractItemsBig(cell, stack, Actionable.MODULATE, env.getActionSource())));
        }
    }

    public boolean shouldCheckCell() {
        return false;
    }

    public boolean getCheckMode() {
        return false;
    }

    /** Compatibility entry point: finite-capacity checking no longer applies to this output buffer. */
    public void setCheckMode(boolean ignored) {}

    public void addToCache(@NotNull T stack) {
        addToCacheBig(stack);
    }

    public void addToCacheBig(final T stack) {
        if (stack == null || BigAEStackValues.get(stack)
            .signum() <= 0 || isVoidCell) return;
        cache.insertBig(BigAEStackValues.copyWithSize(stack, BigInteger.ZERO), BigAEStackValues.get(stack));
    }

    /**
     * Attempt to store as many stacks as possible into the storage of this output.
     *
     * @param input    The stack to insert. Will be modified by this method (will contain whatever stacks could not be
     *                 inserted; stackSize will be 0 when everything was inserted).
     * @param simulate When true this output will not be modified.
     * @return True if the stack was fully inserted into the output, false otherwise.
     */
    public boolean storePartial(@NotNull T input, boolean simulate) {
        return storePartialBig(input, simulate);
    }

    public boolean storePartialBig(@NotNull T input, boolean simulate) {
        final BigInteger requested = BigAEStackValues.get(input);
        if (requested.signum() < 0) throw new IllegalArgumentException("Negative output amount");
        if (requested.signum() == 0) return true;
        if (!canStore(input)) return false;
        if (!simulate) {
            addToCacheBig(input);
            updateLastInputTick();
            env.dispatchMarkDirty();
        }
        BigAEStackValues.set(input, BigInteger.ZERO);
        return true;
    }

    public boolean isFiltered() {
        if (handler == null) return false;
        return handler.isPreformatted();
    }

    public boolean isWhiteList() {
        if (handler == null) return false;
        return handler.getWhitelist() == IncludeExclude.WHITELIST;
    }

    public boolean isDistribution() {
        if (handler == null) return false;
        return com.silvia.apeiron.compat.CellUpgradePolicies.distribution(handler);
    }

    public boolean canVoidOverflow() {
        if (handler == null) return false;
        return com.silvia.apeiron.compat.CellUpgradePolicies.overflow(handler)
            || handler.getCellInv() instanceof VoidCellInventory<?>;
    }

    public boolean canStore(@NotNull T input) {
        if (handler == null || !handler.isPreformatted()) return true;
        return handler.canAccept(input);
    }

    public long getCellAvailableSpace() {
        return BigAEStackValues.saturatedLong(getCellAvailableSpaceBig());
    }

    public BigInteger getCellAvailableSpaceBig() {
        if (handler != null && handler.getCellInv() instanceof CellInventory<?>cellInv) {
            return cellInv instanceof BigCellInventory ? ((BigCellInventory) cellInv).getRemainingItemCountBig()
                : BigInteger.valueOf(cellInv.getRemainingItemCount());
        }
        return BigInteger.ZERO;
    }

    public boolean getCacheMode() {
        return cacheMode;
    }

    public void setCacheMode(boolean cacheMode) {
        this.cacheMode = cacheMode;
        updateState();
        updateCellArray();
        env.dispatchMarkDirty();
        env.notifyOutputSpaceChanged();
        EntityPlayer p = env.getLastClickedPlayer();
        if (p != null && GTUtility.isServer()) {
            GTUtility.sendChatTrans(p, "GT5U.hatch.outputme.cacheMode." + this.cacheMode);
            if (cacheMode) {
                GTUtility.sendChatTrans(p, "GT5U.hatch.outputme.cacheMode.desc");
            }
        }
    }

    public void saveNBTData(NBTTagCompound aNBT) {
        writeCacheAndSettings(aNBT);
        getProxy().writeToNBT(aNBT);
    }

    private void writeCacheAndSettings(NBTTagCompound aNBT) {
        NBTTagList cacheTag = new NBTTagList();
        cache.iterateAllBig((s, amount) -> {
            NBTTagCompound tag = env.saveStackToNBT(BigAEStackValues.copyWithSize(s, amount));
            cacheTag.appendTag(tag);
        });

        aNBT.setBoolean("additionalConnection", additionalConnection);
        aNBT.setTag("cache", cacheTag);
        aNBT.removeTag("baseCapacity");
        aNBT.removeTag("ApeironBaseCapacity");
        aNBT.removeTag("checkMode");
        aNBT.setBoolean("cacheMode", cacheMode);
        aNBT.setInteger("myPriority", myPriority);
    }

    public void loadNBTData(NBTTagCompound aNBT) {
        cache.clear();
        NBTBase cacheTag = aNBT.getTag("cache");
        if (cacheTag instanceof NBTTagList cacheTagList) {
            for (int i = 0; i < cacheTagList.tagCount(); ++i) {
                NBTTagCompound tag = cacheTagList.getCompoundTagAt(i);
                var input = env.loadStackFromNBT(tag);
                if (input == null) continue;
                cache.insertBig(BigAEStackValues.copyWithSize(input, BigInteger.ZERO), BigAEStackValues.get(input));
            }
        }

        additionalConnection = aNBT.getBoolean("additionalConnection");
        cacheMode = aNBT.getBoolean("cacheMode");
        myPriority = aNBT.getInteger("myPriority");
        this.isCached = false;
        if (aNBT.hasKey("proxy")) getProxy().readFromNBT(aNBT);
        oldCellStack = env.getCellStack();
        updateState();
        updateAE2ProxyColor();
    }

    public void updateAE2ProxyColor() {
        AENetworkProxy proxy = getProxy();
        byte color = env.getColor();
        if (color == -1) {
            proxy.setColor(AEColor.Transparent);
        } else {
            proxy.setColor(AEColor.values()[Dyes.transformDyeIndex(color)]);
        }
        if (proxy.getNode() != null) {
            proxy.getNode()
                .updateState();
        }
    }

    public void updateCellArray() {
        try {
            this.getProxy()
                .getGrid()
                .postEvent(new MENetworkCellArrayUpdate());
        } catch (Exception ignored) {}
    }

    public NBTTagCompound getCopiedData(EntityPlayer player) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("type", env.getCopiedDataIdentifier(player));
        tag.setBoolean("additionalConnection", additionalConnection);
        tag.setByte("color", env.getColor());
        tag.setBoolean("cacheMode", getCacheMode());
        tag.setInteger("myPriority", getPriority());
        return tag;
    }

    public boolean pasteCopiedData(EntityPlayer player, NBTTagCompound nbt) {
        if (nbt == null || !env.getCopiedDataIdentifier(player)
            .equals(nbt.getString("type"))) return false;
        setAdditionalConnection(nbt.getBoolean("additionalConnection"));
        byte color = nbt.getByte("color");
        env.getBaseMetaTileEntity()
            .setColorization(color);
        setCacheMode(nbt.getBoolean("cacheMode"));
        setPriority(nbt.getInteger("myPriority"));
        return true;
    }

    public void writeToClientPacket(ByteBuf buffer) {
        buffer.writeBoolean(isCacheUnlimited());
    }

    public void readFromClientPacket(ByteBuf buffer) {
        if (!buffer.readBoolean()) throw new IllegalArgumentException("ME output buffer must be unlimited");
    }

    public List<T> getCacheList() {
        List<T> stackList = new ArrayList<>();

        cache.iterateAllBig((stack, amount) -> stackList.add(BigAEStackValues.copyWithSize(stack, amount)));

        return stackList;
    }

    public void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {
        tooltip.add(StatCollector.translateToLocal("apeiron.machine.me_output.unlimited_cache"));
    }

    private void processWailaNBTData(NBTTagCompound tag, String listKey, String countKey, List<T> stacks) {
        tag.setInteger(countKey, stacks.size());

        NBTTagList tagList = new NBTTagList();
        tag.setTag(listKey, tagList);

        stacks.sort(
            (a, b) -> BigAEStackValues.get(b)
                .compareTo(BigAEStackValues.get(a)));
        stacks.stream()
            .limit(10)
            .forEach(stack -> {
                NBTTagCompound stackTag = new NBTTagCompound();
                stack.writeToNBT(stackTag);
                BigValueCodec
                    .writeNBT(stackTag, "Amount", "ApeironAmount", new AdaptiveInteger(BigAEStackValues.get(stack)));
                tagList.appendTag(stackTag);
            });
    }

    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        tag.setBoolean("ApeironUnlimitedCache", isCacheUnlimited());
        processWailaNBTData(tag, "stacks", "stackCount", getCacheList());

        if (cacheMode && cell != null) {
            List<T> cacheList = new ArrayList<>();
            final Iterable<T> iter = cell.getAvailableItems(
                cell.getStackType()
                    .createPrimitiveList(),
                IterationCounter.fetchNewId());
            iter.forEach(cacheList::add);
            processWailaNBTData(tag, "cacheStacks", "cacheCount", cacheList);
        }
    }

    private void processInfoData(String langBaseKey, Function<T, String> nameGetter, List<T> list, List<String> ss) {
        if (list.isEmpty()) {
            ss.add(langBaseKey + ".empty");
        } else {
            ss.add(IGregTechDeviceInformation.encode(langBaseKey + ".contains", list.size()));
            list.stream()
                .limit(100)
                .forEach(s -> {
                    ss.add(
                        nameGetter.apply(s) + ": "
                            + EnumChatFormatting.GOLD
                            + BigNumberFormatter.formatExact(BigAEStackValues.get(s))
                            + " "
                            + getFluidUnit()
                            + EnumChatFormatting.RESET);
                });
        }
    }

    public String[] getInfoData(String AEDiagnostics, String langBaseKey, Function<T, String> nameGetter) {
        List<String> ss = new ArrayList<>();
        ss.add(
            (getProxy() != null && getProxy().isActive()) ? "GT5U.infodata.hatch.crafting_input_me.bus.online"
                : IGregTechDeviceInformation
                    .encode("GT5U.infodata.hatch.crafting_input_me.bus.offline", AEDiagnostics));
        ss.add("apeiron.machine.me_output.unlimited_cache");
        processInfoData(langBaseKey, nameGetter, getCacheList(), ss);
        if (cacheMode && cell != null) {
            List<T> cacheList = new ArrayList<>();
            final Iterable<T> iter = cell.getAvailableItems(
                cell.getStackType()
                    .createPrimitiveList(),
                IterationCounter.fetchNewId());
            iter.forEach(cacheList::add);
            ss.add("GT5U.waila.hatch.outputme.storage_cache");
            processInfoData(langBaseKey, nameGetter, cacheList, ss);
        }
        return ss.toArray(new String[0]);
    }

    @SideOnly(Side.CLIENT)
    public static class WailaHelper {

        @Nullable
        private static String getLocalizedName(String prefix, NBTTagCompound stackTag) {
            if ("fluid".equals(prefix)) {
                FluidStack fluid = FluidStack.loadFluidStackFromNBT(stackTag);
                return fluid == null ? null : fluid.getLocalizedName();
            }
            ItemStack stack = ItemStack.loadItemStackFromNBT(stackTag);
            return stack == null ? null : stack.getDisplayName();
        }

        private static void processWailaAdvancedBody(String prefix, List<String> ss, String listKey, String countKey,
            NBTTagCompound tag) {
            NBTTagList stacks = tag.getTagList(listKey, 10);
            int stackCount = tag.getInteger(countKey);

            if (stackCount == 0) {
                ss.add(StatCollector.translateToLocal("GT5U.waila.hatch.outputme." + prefix + "_cache_empty"));
                return;
            }
            ss.add(
                StatCollector.translateToLocalFormatted(
                    "GT5U.waila.hatch.outputme." + prefix + "_cache_detail",
                    stackCount,
                    stackCount > 1 ? "s" : ""));

            for (int i = 0; i < stacks.tagCount(); i++) {
                NBTTagCompound stackTag = stacks.getCompoundTagAt(i);
                // Names must be resolved client-side, otherwise a dedicated server sends its own localization
                String name = getLocalizedName(prefix, stackTag);
                if (name == null) continue;

                ss.add(
                    String.format(
                        "%s: %s%s%s",
                        name,
                        EnumChatFormatting.GOLD,
                        BigNumberFormatter.formatExact(
                            BigValueCodec.readNBT(stackTag, "Amount", "ApeironAmount")
                                .toBigInteger()),
                        EnumChatFormatting.RESET));
            }

            if (stackCount > stacks.tagCount()) {
                ss.add(
                    StatCollector.translateToLocalFormatted(
                        "GT5U.waila.hatch.outputme." + prefix + "_cache_detail.more",
                        stackCount - stacks.tagCount()));
            }
        }

        public static void getWailaAdvancedBody(String prefix, List<String> ss, IWailaDataAccessor accessor) {
            getWailaAdvancedBody(prefix, ss, accessor.getNBTData());
        }

        public static void getWailaAdvancedBody(String prefix, List<String> ss, NBTTagCompound tag) {
            processWailaAdvancedBody(prefix, ss, "stacks", "stackCount", tag);
            if (tag.hasKey("cacheCount")) {
                ss.add(StatCollector.translateToLocal("GT5U.waila.hatch.outputme.storage_cache"));
                processWailaAdvancedBody(prefix, ss, "cacheStacks", "cacheCount", tag);
            }
        }
    }
}
