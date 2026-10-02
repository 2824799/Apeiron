// SPDX-License-Identifier: MIT
// Pattern provider, buffering, programming and block-local multipliers ported from Programmable Hatches (c) 2024 reobf.
package com.silvia.apeiron.common.machine.me.input;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.glodblock.github.common.item.ItemFluidPacket;
import com.silvia.apeiron.ae.automation.BigPoweredTransfers;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.me.input.BigDualInputHatch;
import com.silvia.apeiron.client.gui.machine.me.input.InfinitePatternInputGui;
import com.silvia.apeiron.common.machine.me.input.pattern.MultipliedPatternDetails;
import com.silvia.apeiron.common.machine.me.input.storage.BigPatternBuffer;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigNumberFormatter;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.config.Actionable;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IInterfaceViewable;
import appeng.helpers.ICustomNameObject;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.util.inv.MEInventoryCrafting;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEFluidStackType;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;
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
public class MTEInfinitePatternInputAssembly extends MTEHatchInputBus implements BigDualInputHatch, ICraftingProvider,
    IGridProxyable, IActionHost, IPowerChannelState, IInterfaceViewable, ICustomNameObject {

    public static final int PATTERN_COUNT = 360;
    public static final int PATTERN_COLUMNS = 10;
    public static final int TERMINAL_COLUMNS = 9;
    public static final int BUFFER_COUNT = 24;
    public static final int CATALYST_SLOTS = 9;
    public static final int SHARED_CATALYST_START = PATTERN_COUNT;
    public static final int PATTERN_CATALYST_START = SHARED_CATALYST_START + CATALYST_SLOTS;
    private static final int INVENTORY_SIZE = PATTERN_CATALYST_START + PATTERN_COUNT * CATALYST_SLOTS;
    private static final String STATE_KEY = "ApeironPatternInput";
    private final BigInteger[] multipliers = new BigInteger[PATTERN_COUNT];
    private final ICraftingPatternDetails[] details = new ICraftingPatternDetails[PATTERN_COUNT];
    private final Map<ICraftingPatternDetails, Integer> detailSlots = new HashMap<>();
    private final List<BigPatternBuffer> buffers = new ArrayList<>();
    private final Map<gregtech.common.tileentities.machines.IHatchWatcher, Integer> watcherReferences = new java.util.IdentityHashMap<>();
    private final IInventory patternInventory = new PatternInventory();
    private AENetworkProxy proxy;
    private boolean patternDirty = true;
    private boolean additionalConnection;
    private boolean processing;
    private boolean multiplierView;
    private boolean patternOptimization = true;
    private boolean terminalVisible = true;
    private BigInteger savedPushCalls = BigInteger.ZERO;
    private String customName = "";
    private MachineSource requestSource;

    public MTEInfinitePatternInputAssembly(int id, String name, String regionalName) {
        super(id, name, regionalName, 10, INVENTORY_SIZE, null);
        initialize();
    }

    protected MTEInfinitePatternInputAssembly(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, INVENTORY_SIZE, description, textures);
        initialize();
    }

    private void initialize() {
        disableSort = true;
        Arrays.fill(multipliers, BigInteger.ONE);
        for (int i = 0; i < BUFFER_COUNT; i++) buffers.add(new BigPatternBuffer());
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEInfinitePatternInputAssembly(mName, mTier, mDescriptionArray, mTextures);
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
            .translateToLocal("gt.blockmachines.apeiron.infinite_pattern_input.name");
    }

    @Override
    public String[] getDescription() {
        return GTSplit.splitLocalized("apeiron.machine.infinite_pattern_input.desc");
    }

    @Override
    public boolean allowSelectCircuit() {
        return false;
    }

    @Override
    public ITexture[] getTexturesActive(ITexture base) {
        return new ITexture[] { base, TextureFactory.of(BlockIcons.OVERLAY_ME_CRAFTING_INPUT_BUFFER) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture base) {
        return new ITexture[] { base, TextureFactory.of(BlockIcons.OVERLAY_ME_CRAFTING_INPUT_BUFFER) };
    }

    @Override
    public MTEInfinitePatternInputAssembly getInputSource() {
        return this;
    }

    public List<BigPatternBuffer> getBuffers() {
        return Collections.unmodifiableList(buffers);
    }

    public boolean isMultiplierView() {
        return multiplierView;
    }

    public void setMultiplierView(boolean value) {
        multiplierView = value;
        markDirty();
    }

    @Override
    public boolean allowsPatternOptimization() {
        return patternOptimization;
    }

    public void setPatternOptimization(boolean value) {
        patternOptimization = value;
        markDirty();
    }

    public BigInteger getSavedPushCallsBig() {
        return savedPushCalls;
    }

    public boolean isRecipeProcessing() {
        return processing;
    }

    public void setTerminalVisible(boolean visible) {
        terminalVisible = visible;
        markDirty();
    }

    public void setBufferLocked(int index, boolean locked) {
        if (processing || index < 0 || index >= BUFFER_COUNT) return;
        buffers.get(index)
            .setLocked(locked);
        markDirty();
        notifyWatchers();
    }

    /** Manual transfers obey the same exact ledger and cannot run during recipe consumption. */
    public boolean addToBufferBig(int index, List<IAEStack<?>> input) {
        if (processing || index < 0 || index >= BUFFER_COUNT) return false;
        if (!buffers.get(index)
            .add(input, BigInteger.ONE)) return false;
        markDirty();
        notifyWatchers();
        return true;
    }

    public void removeFromBufferBig(int index, IAEStack<?> type, BigInteger amount) {
        if (processing) throw new IllegalStateException("Recipe input is in use");
        buffers.get(index)
            .removeBig(type, amount);
        markDirty();
        notifyWatchers();
    }

    public int catalystSlotStart(int patternSlot) {
        checkPatternSlot(patternSlot);
        return PATTERN_CATALYST_START + patternSlot * CATALYST_SLOTS;
    }

    private static void checkPatternSlot(int index) {
        if (index < 0 || index >= PATTERN_COUNT) throw new IndexOutOfBoundsException("Pattern slot " + index);
    }

    public BigInteger getMultiplierBig(int index) {
        checkPatternSlot(index);
        return multipliers[index];
    }

    public void setMultiplierBig(int index, BigInteger value) {
        checkPatternSlot(index);
        if (value.signum() <= 0) throw new IllegalArgumentException("Pattern multiplier must be positive");
        if (multipliers[index].equals(value)) return;
        multipliers[index] = value;
        refreshPatterns();
    }

    public void scaleMultipliers(boolean halve, boolean reset) {
        for (int i = 0; i < PATTERN_COUNT; i++) multipliers[i] = reset ? BigInteger.ONE
            : halve ? multipliers[i].divide(BigInteger.valueOf(2))
                .max(BigInteger.ONE) : multipliers[i].shiftLeft(1);
        refreshPatterns();
    }

    public void refreshPatterns() {
        Arrays.fill(details, null);
        detailSlots.clear();
        patternDirty = true;
        markDirty();
        notifyWatchers();
    }

    @Override
    public void addWatcher(gregtech.common.tileentities.machines.IHatchWatcher watcher) {
        int references = watcherReferences.getOrDefault(watcher, 0);
        watcherReferences.put(watcher, references + 1);
        if (references == 0) super.addWatcher(watcher);
    }

    @Override
    public void removeWatcher(gregtech.common.tileentities.machines.IHatchWatcher watcher) {
        Integer references = watcherReferences.get(watcher);
        if (references == null) return;
        if (references == 1) {
            watcherReferences.remove(watcher);
            super.removeWatcher(watcher);
        } else watcherReferences.put(watcher, references - 1);
    }

    @Override
    public boolean shouldDropItemAt(int index) {
        // Physical patterns and catalysts are preserved in the machine item, so block destruction must not drop them
        // again.
        return false;
    }

    @Override
    public void onContentsChanged(int slot) {
        if (slot < PATTERN_COUNT) refreshPatterns();
        else {
            updateManualSelectors();
            markDirty();
            notifyWatchers();
        }
    }

    private List<ItemStack> manualSelectors(int index) {
        List<ItemStack> result = new ArrayList<>();
        addCatalysts(result, SHARED_CATALYST_START);
        if (index >= 0 && index < PATTERN_COUNT) addCatalysts(result, catalystSlotStart(index));
        return result;
    }

    private void updateManualSelectors() {
        if (processing) return;
        for (BigPatternBuffer buffer : buffers)
            if (buffer.getPatternSlot() >= 0) buffer.updateManualSelectors(manualSelectors(buffer.getPatternSlot()));
    }

    public ICraftingPatternDetails getPatternDetails(int index) {
        checkPatternSlot(index);
        if (details[index] != null) return details[index];
        ItemStack pattern = mInventory[index];
        if (pattern == null || !(pattern.getItem() instanceof ICraftingPatternItem)) return null;
        World world = getBaseMetaTileEntity() == null ? null : getBaseMetaTileEntity().getWorld();
        ICraftingPatternDetails base = ((ICraftingPatternItem) pattern.getItem()).getPatternForItem(pattern, world);
        if (base == null) return null;
        details[index] = multipliers[index].equals(BigInteger.ONE) ? base
            : new MultipliedPatternDetails(base, multipliers[index]);
        detailSlots.put(details[index], index);
        return details[index];
    }

    @Override
    public void provideCrafting(ICraftingProviderHelper helper) {
        if (!isActive()) return;
        for (int i = 0; i < PATTERN_COUNT; i++) {
            ICraftingPatternDetails pattern = getPatternDetails(i);
            if (pattern != null) helper.addCraftingOption(this, pattern);
        }
    }

    private int findPattern(ICraftingPatternDetails pattern) {
        Integer index = detailSlots.get(pattern);
        if (index != null) return index;
        for (int i = 0; i < PATTERN_COUNT; i++) if (pattern.equals(getPatternDetails(i))) return i;
        return -1;
    }

    private List<ItemStack> catalysts(int index, List<IAEStack<?>> input, List<IAEStack<?>> consumables) {
        List<ItemStack> result = new ArrayList<>();
        addCatalysts(result, SHARED_CATALYST_START);
        addCatalysts(result, catalystSlotStart(index));
        for (IAEStack<?> stack : input) {
            if (stack == null || BigAEStackValues.get(stack)
                .signum() <= 0) continue;
            if (stack instanceof IAEItemStack && ((IAEItemStack) stack).getItemStack()
                .getItem()
                .getClass()
                .getName()
                .equals("reobf.proghatches.item.ItemProgrammingCircuit")) {
                ItemStack wrapper = ((IAEItemStack) stack).getItemStack();
                if (wrapper.hasTagCompound() && wrapper.getTagCompound()
                    .hasKey("targetCircuit")) {
                    NBTTagCompound circuit = (NBTTagCompound) wrapper.getTagCompound()
                        .getCompoundTag("targetCircuit")
                        .copy();
                    if (circuit.hasKey("string_id")) circuit.setInteger(
                        "id",
                        Item.itemRegistry.getIDForObject(Item.itemRegistry.getObject(circuit.getString("string_id"))));
                    ItemStack decoded = ItemStack.loadItemStackFromNBT(circuit);
                    if (decoded != null) {
                        if (decoded.getItemDamage() == net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE)
                            decoded.setItemDamage(0);
                        decoded.stackSize = 0;
                        result.add(decoded);
                    }
                }
            } else consumables.add(stack.copy());
        }
        return result;
    }

    private void addCatalysts(List<ItemStack> result, int start) {
        for (int i = 0; i < CATALYST_SLOTS; i++) if (mInventory[start + i] != null) {
            ItemStack selector = mInventory[start + i].copy();
            selector.stackSize = 0;
            result.add(selector);
        }
    }

    /** Inserts all exact inputs atomically. Caller owns the requested number of complete batches. */
    public boolean pushPatternBig(ICraftingPatternDetails pattern, List<IAEStack<?>> input, BigInteger batches,
        boolean simulate) {
        if (batches.signum() <= 0 || !isActive() || processing) return false;
        int index = findPattern(pattern);
        if (index < 0) return false;
        List<IAEStack<?>> consumables = new ArrayList<>();
        for (IAEStack<?> stack : input) if (stack != null && BigAEStackValues.get(stack)
            .signum() < 0) throw new IllegalArgumentException("Negative pattern input");
        List<ItemStack> selectors = catalysts(index, input, consumables);
        BigPatternBuffer target = null;
        for (BigPatternBuffer buffer : buffers) if (buffer.belongsTo(index, mInventory[index], selectors)
            && (buffer.isEmpty() && !buffer.isLocked() || buffer.hasSameRecipeInputs(consumables))) {
                target = buffer;
                break;
            }
        if (target == null) for (BigPatternBuffer buffer : buffers) if (buffer.isEmpty() && !buffer.isLocked()) {
            target = buffer;
            break;
        }
        if (target == null) return false;
        if (!target.isEmpty() && !target.canAdd(consumables)) return false;
        if (target.isEmpty()) {
            BigPatternBuffer preview = new BigPatternBuffer();
            if (!preview.canAdd(consumables)) return false;
        }
        if (simulate) return true;
        if (target.isEmpty() && !target.isLocked()) {
            target.assign(index, mInventory[index], selectors, manualSelectors(index).size());
            target.setRecipeInputs(consumables);
        }
        if (!target.add(consumables, batches)) throw new IllegalStateException("Input preflight disagreed with commit");
        savedPushCalls = savedPushCalls.add(batches.subtract(BigInteger.ONE));
        markDirty();
        notifyWatchers();
        return true;
    }

    @Override
    public boolean pushPattern(ICraftingPatternDetails pattern, InventoryCrafting table) {
        List<IAEStack<?>> inputs = new ArrayList<>();
        for (int i = 0; i < table.getSizeInventory(); i++) {
            IAEStack<?> exact = table instanceof MEInventoryCrafting ? ((MEInventoryCrafting) table).getAEStackInSlot(i)
                : null;
            if (exact != null) inputs.add(exact.copy());
            else {
                ItemStack stack = table.getStackInSlot(i);
                if (stack == null) continue;
                inputs.add(
                    stack.getItem() instanceof ItemFluidPacket
                        ? AEFluidStack.create(ItemFluidPacket.getFluidStack(stack))
                        : AEItemStack.create(stack));
            }
        }
        // Read AE's exact table, never re-derive its quantities from saturated physical packets.
        return pushPatternBig(pattern, inputs, BigInteger.ONE, false);
    }

    @Override
    public boolean isBusy() {
        return processing || !isActive();
    }

    @Override
    public BlockingMode getBlockingMode() {
        return BlockingMode.BLOCKING;
    }

    public void beginRecipeProcessing() {
        if (processing) return;
        for (BigPatternBuffer buffer : buffers) buffer.reconcile();
        updateManualSelectors();
        processing = true;
    }

    public void endRecipeProcessing() {
        if (!processing) return;
        for (BigPatternBuffer buffer : buffers) buffer.reconcile();
        processing = false;
        markDirty();
    }

    @Override
    public Iterator<BigPatternBuffer> inventories() {
        return buffers.stream()
            .filter(buffer -> !buffer.isEmpty())
            .iterator();
    }

    @Override
    public Optional<IDualInputInventory> getFirstNonEmptyInventory() {
        return buffers.stream()
            .filter(buffer -> !buffer.isEmpty())
            .map(buffer -> (IDualInputInventory) buffer)
            .findFirst();
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
    public ItemStack getCrafterIcon() {
        return getMachineCraftingIcon() == null ? getStackForm(1) : getMachineCraftingIcon().copy();
    }

    @Override
    public void updateSlots() {
        if (!processing) for (BigPatternBuffer buffer : buffers) buffer.reconcile();
    }

    public void refundAll() {
        if (processing || !isActive()) return;
        try {
            for (BigPatternBuffer buffer : buffers) {
                buffer.reconcile();
                for (IAEStack<?> input : buffer.getStacksBig()) {
                    IMEInventory<?> storage = input instanceof IAEFluidStack ? getProxy().getStorage()
                        .getFluidInventory()
                        : getProxy().getStorage()
                            .getItemInventory();
                    IAEStack<?> rest = BigPoweredTransfers.poweredInsertBulkBig(
                        getProxy().getEnergy(),
                        storage,
                        input.copy(),
                        getRequestSource(),
                        Actionable.MODULATE);
                    buffer.removeBig(
                        input,
                        BigAEStackValues.get(input)
                            .subtract(BigAEStackValues.get(rest)));
                }
            }
            markDirty();
            notifyWatchers();
        } catch (GridAccessException ignored) {}
    }

    private MachineSource getRequestSource() {
        if (requestSource == null) requestSource = new MachineSource(this);
        return requestSource;
    }

    @Override
    public AENetworkProxy getProxy() {
        if (proxy == null) {
            proxy = new AENetworkProxy(this, "proxy", getStackForm(1), true);
            proxy.setFlags(GridFlags.REQUIRE_CHANNEL);
            updateConnections();
            if (getBaseMetaTileEntity().getWorld() != null) proxy.setOwner(
                getBaseMetaTileEntity().getWorld()
                    .getPlayerEntityByName(getBaseMetaTileEntity().getOwnerName()));
        }
        return proxy;
    }

    private void updateConnections() {
        if (proxy == null || getBaseMetaTileEntity() == null) return;
        proxy.setValidSides(
            additionalConnection ? EnumSet.complementOf(EnumSet.of(ForgeDirection.UNKNOWN))
                : EnumSet.of(getBaseMetaTileEntity().getFrontFacing()));
    }

    @Override
    public void onFirstTick(IGregTechTileEntity tile) {
        super.onFirstTick(tile);
        getProxy().onReady();
    }

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        if (!tile.isServerSide()) return;
        if (patternDirty && getProxy().isActive()) try {
            getProxy().getGrid()
                .postEvent(new MENetworkCraftingPatternChange(this, getProxy().getNode()));
            patternDirty = false;
        } catch (GridAccessException ignored) {}
        if (tick % 20 == 0) tile.setActive(isActive());
    }

    @Override
    public void onRemoval() {
        if (proxy != null) proxy.invalidate();
        super.onRemoval();
    }

    @Override
    public void onUnload() {
        if (proxy != null) proxy.onChunkUnload();
    }

    @Override
    public void onFacingChange() {
        updateConnections();
    }

    @Override
    public boolean onWireCutterRightClick(ForgeDirection side, ForgeDirection wrenchSide, EntityPlayer player, float x,
        float y, float z, ItemStack tool) {
        additionalConnection = !additionalConnection;
        updateConnections();
        markDirty();
        return true;
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
        return additionalConnection || side == getBaseMetaTileEntity().getFrontFacing() ? AECableType.SMART
            : AECableType.NONE;
    }

    @Override
    public void gridChanged() {
        patternDirty = true;
    }

    @Override
    public void securityBreak() {
        getBaseMetaTileEntity().getWorld()
            .func_147480_a(
                getBaseMetaTileEntity().getXCoord(),
                getBaseMetaTileEntity().getYCoord(),
                getBaseMetaTileEntity().getZCoord(),
                true);
    }

    @Override
    public boolean isActive() {
        return getProxy().isActive();
    }

    @Override
    public boolean isPowered() {
        return getProxy().isPowered();
    }

    @Override
    public TileEntity getTileEntity() {
        return (TileEntity) getBaseMetaTileEntity();
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(getTileEntity());
    }

    @Override
    public int rowSize() {
        return TERMINAL_COLUMNS;
    }

    @Override
    public int rows() {
        return PATTERN_COUNT / TERMINAL_COLUMNS;
    }

    @Override
    public IInventory getPatterns() {
        return patternInventory;
    }

    @Override
    public IAEStackType<?>[] getSupportedStackTypes() {
        return new IAEStackType<?>[] { AEItemStackType.ITEM_STACK_TYPE, AEFluidStackType.FLUID_STACK_TYPE };
    }

    @Override
    public boolean shouldDisplay() {
        return terminalVisible;
    }

    @Override
    public String getName() {
        if (hasCustomName()) return customName;
        final String machines = watchers.stream()
            .filter(watcher -> watcher instanceof MetaTileEntity)
            .map(watcher -> ((MetaTileEntity) watcher).getLocalName())
            .distinct()
            .sorted()
            .collect(java.util.stream.Collectors.joining(" / "));
        if (!machines.isEmpty()) return machines;
        final ItemStack icon = getMachineCraftingIcon();
        return icon == null ? getLocalName() : icon.getDisplayName();
    }

    @Override
    public String getCustomName() {
        return customName;
    }

    @Override
    public boolean hasCustomName() {
        return !customName.isEmpty();
    }

    @Override
    public void setCustomName(String name) {
        customName = name == null ? "" : name;
        refreshPatterns();
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return stack != null && slot >= 0
            && slot < INVENTORY_SIZE
            && (slot >= PATTERN_COUNT || stack.getItem() instanceof ICraftingPatternItem);
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
    public void onLeftclick(IGregTechTileEntity tile, EntityPlayer player) {
        ItemStack stick = player.inventory.getCurrentItem();
        if (tile.isServerSide() && stick != null && ItemList.Tool_DataStick.isStackEqual(stick, false, true)) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("type", "ApeironInfinitePatternInput");
            tag.setInteger("dimension", tile.getWorld().provider.dimensionId);
            tag.setInteger("x", tile.getXCoord());
            tag.setInteger("y", tile.getYCoord());
            tag.setInteger("z", tile.getZCoord());
            stick.setTagCompound(tag);
            player.addChatMessage(new ChatComponentTranslation("apeiron.machine.pattern_input.link_saved"));
            return;
        }
        super.onLeftclick(tile, player);
    }

    private void writeState(NBTTagCompound tag) {
        NBTTagCompound state = new NBTTagCompound();
        state.setBoolean("connections", additionalConnection);
        state.setBoolean("multiplierView", multiplierView);
        state.setBoolean("patternOptimization", patternOptimization);
        state.setBoolean("terminalVisible", terminalVisible);
        BigValueCodec.writeNBT(state, "savedPushCalls", "savedPushCallsBig", new AdaptiveInteger(savedPushCalls));
        state.setString("name", customName);
        NBTTagList factors = new NBTTagList();
        for (int i = 0; i < PATTERN_COUNT; i++) if (!multipliers[i].equals(BigInteger.ONE)) {
            NBTTagCompound factor = new NBTTagCompound();
            factor.setInteger("slot", i);
            BigValueCodec.writeNBT(factor, "multiplier", "big", new AdaptiveInteger(multipliers[i]));
            factors.appendTag(factor);
        }
        state.setTag("multipliers", factors);
        NBTTagList contents = new NBTTagList();
        for (BigPatternBuffer buffer : buffers) contents.appendTag(buffer.writeNBT());
        NBTTagList physicalInventory = new NBTTagList();
        for (int i = 0; i < mInventory.length; i++) if (mInventory[i] != null) {
            NBTTagCompound entry = mInventory[i].writeToNBT(new NBTTagCompound());
            entry.setInteger("slot", i);
            physicalInventory.appendTag(entry);
        }
        state.setTag("inventory", physicalInventory);
        state.setTag("buffers", contents);
        tag.setTag(STATE_KEY, state);
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        writeState(tag);
        getProxy().writeToNBT(tag);
    }

    @Override
    public void setItemNBT(NBTTagCompound tag) {
        super.setItemNBT(tag);
        writeState(tag);
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        processing = false;
        disableSort = true;
        NBTTagCompound state = tag.getCompoundTag(STATE_KEY);
        Arrays.fill(multipliers, BigInteger.ONE);
        if (state.hasKey("inventory")) {
            Arrays.fill(mInventory, null);
            NBTTagList inventory = state.getTagList("inventory", 10);
            for (int i = 0; i < inventory.tagCount(); i++) {
                NBTTagCompound entry = inventory.getCompoundTagAt(i);
                int slot = entry.getInteger("slot");
                if (slot >= 0 && slot < mInventory.length) mInventory[slot] = ItemStack.loadItemStackFromNBT(entry);
            }
        }
        NBTTagList factors = state.getTagList("multipliers", 10);
        for (int i = 0; i < factors.tagCount(); i++) {
            NBTTagCompound factor = factors.getCompoundTagAt(i);
            int slot = factor.getInteger("slot");
            if (slot >= 0 && slot < PATTERN_COUNT)
                multipliers[slot] = BigValueCodec.readNBT(factor, "multiplier", "big")
                    .toBigInteger()
                    .max(BigInteger.ONE);
        }
        NBTTagList contents = state.getTagList("buffers", 10);
        if (contents.tagCount() > BUFFER_COUNT) throw new IllegalArgumentException("Too many saved input buffers");
        for (int i = 0; i < BUFFER_COUNT; i++) buffers.get(i)
            .readNBT(contents.getCompoundTagAt(i));
        additionalConnection = state.getBoolean("connections");
        multiplierView = state.getBoolean("multiplierView");
        patternOptimization = !state.hasKey("patternOptimization") || state.getBoolean("patternOptimization");
        terminalVisible = !state.hasKey("terminalVisible") || state.getBoolean("terminalVisible");
        savedPushCalls = BigValueCodec.readNBT(state, "savedPushCalls", "savedPushCallsBig")
            .toBigInteger()
            .max(BigInteger.ZERO);
        customName = state.getString("name");
        if (tag.hasKey("proxy")) getProxy().readFromNBT(tag);
        updateConnections();
        refreshPatterns();
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        return new InfinitePatternInputGui(this).build(data, sync, settings);
    }

    /** A small status packet, not the full contents, is sent for Waila. */
    public void writeBufferStatus(NBTTagCompound tag) {
        NBTTagList statuses = new NBTTagList();
        for (int i = 0; i < BUFFER_COUNT; i++) {
            BigPatternBuffer buffer = buffers.get(i);
            NBTTagCompound status = new NBTTagCompound();
            status.setInteger("slot", i);
            status.setInteger("pattern", buffer.getPatternSlot());
            status.setBoolean("empty", buffer.isEmpty());
            status.setBoolean("processing", processing && !buffer.isEmpty());
            status.setBoolean("locked", buffer.isLocked() || buffer.hasRecipe() && !buffer.isEmpty());
            status.setBoolean("recipe", buffer.hasRecipe() && (!buffer.isEmpty() || buffer.isLocked()));
            status.setString("copies", BigNumberFormatter.formatExact(buffer.getPossibleBatchesBig()));
            NBTTagList recipe = new NBTTagList();
            for (IAEStack<?> ingredient : buffer.getRecipeInputs()) recipe
                .appendTag(com.silvia.apeiron.common.machine.me.input.storage.BigPatternStackCodec.write(ingredient));
            status.setTag("ingredients", recipe);
            NBTTagList circuits = new NBTTagList();
            for (ItemStack circuit : buffer.getSelectors())
                circuits.appendTag(circuit.writeToNBT(new NBTTagCompound()));
            status.setTag("circuits", circuits);
            status.setString("items", BigNumberFormatter.formatCompact(buffer.getItemAmountBig()));
            status.setString("fluids", BigNumberFormatter.formatCompact(buffer.getFluidAmountBig()));
            statuses.appendTag(status);
        }
        tag.setTag("ApeironBufferStatus", statuses);
        tag.setString("ApeironSavedPushCalls", BigNumberFormatter.formatExact(savedPushCalls));
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        writeBufferStatus(tag);
    }

    @Override
    public void getWailaBody(ItemStack stack, List<String> tooltip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(stack, tooltip, accessor, config);
        addBufferStatusTooltip(tooltip, accessor.getNBTData());
    }

    public static void addBufferStatusTooltip(List<String> tooltip, NBTTagCompound tag) {
        NBTTagList statuses = tag.getTagList("ApeironBufferStatus", 10);
        int omitted = 0;
        int emptyShown = 0;
        for (int i = 0; i < statuses.tagCount(); i++) {
            NBTTagCompound status = statuses.getCompoundTagAt(i);
            if (status.getBoolean("empty") && !status.getBoolean("locked") && emptyShown++ >= 5) {
                omitted++;
                continue;
            }
            String key = status.getBoolean("locked") ? "locked"
                : status.getBoolean("empty") ? "ready" : status.getBoolean("processing") ? "processing" : "buffered";
            String color = status.getBoolean("locked") ? "\u00a7c"
                : status.getBoolean("empty") ? "\u00a7a" : status.getBoolean("processing") ? "\u00a7b" : "\u00a7e";
            tooltip.add(
                "#" + (i + 1)
                    + " "
                    + color
                    + net.minecraft.util.StatCollector.translateToLocal("apeiron.machine.pattern_input.state." + key)
                    + "\u00a7r");
            if (status.getBoolean("recipe")) {
                tooltip.add(
                    net.minecraft.util.StatCollector.translateToLocalFormatted(
                        "apeiron.machine.pattern_input.possible_batches",
                        status.getString("copies")));
                NBTTagList recipe = status.getTagList("ingredients", 10);
                for (int j = 0; j < recipe.tagCount(); j++) {
                    IAEStack<?> ingredient = com.silvia.apeiron.common.machine.me.input.storage.BigPatternStackCodec
                        .read(recipe.getCompoundTagAt(j));
                    if (ingredient == null) continue;
                    String name = ingredient instanceof IAEItemStack ? ((IAEItemStack) ingredient).getItemStack()
                        .getDisplayName()
                        : ((IAEFluidStack) ingredient).getFluidStack()
                            .getLocalizedName();
                    tooltip.add(
                        "  " + name + " \u00d7 " + BigNumberFormatter.formatExact(BigAEStackValues.get(ingredient)));
                }
                NBTTagList circuits = status.getTagList("circuits", 10);
                for (int j = 0; j < circuits.tagCount(); j++) {
                    ItemStack circuit = ItemStack.loadItemStackFromNBT(circuits.getCompoundTagAt(j));
                    if (circuit != null) tooltip.add("  \u00a7b" + circuit.getDisplayName() + "\u00a7r");
                }
            } else if (!status.getBoolean("empty"))
                tooltip.add("  " + status.getString("items") + " / " + status.getString("fluids") + " L");
        }
        if (omitted > 0) tooltip.add(
            net.minecraft.util.StatCollector
                .translateToLocalFormatted("apeiron.machine.pattern_input.more_ready", omitted));
        tooltip.add(
            net.minecraft.util.StatCollector.translateToLocalFormatted(
                "apeiron.machine.pattern_input.saved_calls",
                tag.getString("ApeironSavedPushCalls")));
    }

    private final class PatternInventory implements IInventory {

        @Override
        public int getSizeInventory() {
            return PATTERN_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            checkPatternSlot(slot);
            return mInventory[slot];
        }

        @Override
        public void setInventorySlotContents(int slot, ItemStack stack) {
            checkPatternSlot(slot);
            mInventory[slot] = stack;
            onContentsChanged(slot);
        }

        @Override
        public ItemStack decrStackSize(int slot, int count) {
            ItemStack stack = getStackInSlot(slot);
            if (stack == null) return null;
            ItemStack result = stack.splitStack(count);
            if (stack.stackSize <= 0) mInventory[slot] = null;
            onContentsChanged(slot);
            return result;
        }

        @Override
        public ItemStack getStackInSlotOnClosing(int slot) {
            ItemStack stack = getStackInSlot(slot);
            setInventorySlotContents(slot, null);
            return stack;
        }

        @Override
        public String getInventoryName() {
            return getName();
        }

        @Override
        public boolean hasCustomInventoryName() {
            return hasCustomName();
        }

        @Override
        public int getInventoryStackLimit() {
            return 1;
        }

        @Override
        public void markDirty() {
            refreshPatterns();
        }

        @Override
        public boolean isUseableByPlayer(EntityPlayer player) {
            return MTEInfinitePatternInputAssembly.this.getBaseMetaTileEntity()
                .isUseableByPlayer(player);
        }

        @Override
        public void openInventory() {}

        @Override
        public void closeInventory() {}

        @Override
        public boolean isItemValidForSlot(int slot, ItemStack stack) {
            return MTEInfinitePatternInputAssembly.this.isItemValidForSlot(slot, stack);
        }
    }
}
