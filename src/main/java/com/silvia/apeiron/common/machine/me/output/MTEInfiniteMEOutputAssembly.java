// SPDX-License-Identifier: LGPL-3.0-only
// Extends the GT5-Unofficial output implementation ported by Apeiron.
package com.silvia.apeiron.common.machine.me.output;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.silvia.apeiron.client.gui.machine.me.output.InfiniteMEOutputAssemblyGui;
import com.silvia.apeiron.common.machine.me.output.storage.BigMEOutputProvider;

import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTSplit;
import io.netty.buffer.ByteBuf;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/** One placed device and one grid node, with separate item/fluid providers and transactions. */
@IMetaTileEntity.SkipGenerateDescription
public class MTEInfiniteMEOutputAssembly extends MTEBoundlessMEOutputBus {

    private static final String FLUID_NBT = "ApeironAssemblyFluids";
    private final FluidPort fluidPort = new FluidPort();

    public MTEInfiniteMEOutputAssembly(int id, String name, String regionalName) {
        super(id, name, regionalName, 2);
    }

    private MTEInfiniteMEOutputAssembly(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, description, textures, 2);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEInfiniteMEOutputAssembly(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public String[] getDescription() {
        return GTSplit.splitLocalized("apeiron.machine.infinite_me_output_assembly.desc");
    }

    /** Fluid view registered with the controller; never a second placed tile or grid node. */
    public MTEBoundlessMEOutputHatch getFluidOutput() {
        return fluidPort;
    }

    public BigMEOutputProvider<IAEFluidStack> getFluidProvider() {
        return fluidPort.getProvider();
    }

    public BigInteger fillBig(FluidStack type, BigInteger amount, boolean doFill) {
        return fluidPort.fillBig(type, amount, doFill);
    }

    @Override
    public int fill(FluidStack fluid, boolean doFill) {
        return fluid == null || fluidPort == null ? 0 : fluidPort.fill(fluid, doFill);
    }

    @Override
    public int fill(ForgeDirection side, FluidStack fluid, boolean doFill) {
        return fill(fluid, doFill);
    }

    @Override
    public boolean canFill(ForgeDirection side, Fluid fluid) {
        return fluid != null && fluidPort != null && fluidPort.canStoreFluid(new FluidStack(fluid, 1));
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        if (index == 0) return super.isItemValidForSlot(index, stack);
        return index == 1 && fluidPort != null && fluidPort.isItemValidForSlot(0, stack);
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity tile, int index, ForgeDirection side, ItemStack stack) {
        return side == tile.getFrontFacing() && isItemValidForSlot(index, stack);
    }

    @Override
    public void onFirstTick(IGregTechTileEntity tile) {
        super.onFirstTick(tile);
        getFluidProvider().updateState();
    }

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        super.onPostTick(tile, tick);
        getFluidProvider().onPostTick(tile, tick);
    }

    @Override
    public void onContentsChanged(int slot) {
        if (slot == 0) super.onContentsChanged(0);
        if (slot == 1 && fluidPort != null) getFluidProvider().onContentsChanged(0);
    }

    @Override
    @MENetworkEventSubscribe
    public void updateCell(MENetworkChannelsChanged event) {
        super.updateCell(event);
        if (fluidPort != null) getFluidProvider().updateCell();
    }

    @Override
    public List<IMEInventoryHandler> getCellArray(StorageChannel channel) {
        return channel == StorageChannel.FLUIDS ? getFluidProvider().getCellArray(channel)
            : super.getCellArray(channel);
    }

    @Override
    public void setConnectsToAllSides(boolean connects) {
        super.setConnectsToAllSides(connects);
        getFluidProvider().setAdditionalConnection(connects);
    }

    @Override
    public boolean onWireCutterRightClick(ForgeDirection side, ForgeDirection wrenchSide, EntityPlayer player, float x,
        float y, float z, ItemStack tool) {
        final boolean changed = super.onWireCutterRightClick(side, wrenchSide, player, x, y, z, tool);
        getFluidProvider().setAdditionalConnection(connectsToAllSides());
        return changed;
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer player, float x, float y, float z,
        ItemStack tool) {
        super.onScrewdriverRightClick(side, player, x, y, z, tool);
        getFluidProvider().onScrewdriverRightClick(side, player, x, y, z, tool);
    }

    private NBTTagCompound fluidState(boolean dropped) {
        final NBTTagCompound tag = new NBTTagCompound();
        if (dropped) getFluidProvider().setItemNBT(tag);
        else getFluidProvider().saveNBTData(tag);
        // The shared node belongs to the placed assembly and is persisted by the item provider once.
        tag.removeTag("proxy");
        return tag;
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        tag.setTag(FLUID_NBT, fluidState(false));
    }

    @Override
    public void setItemNBT(NBTTagCompound tag) {
        super.setItemNBT(tag);
        tag.setTag(FLUID_NBT, fluidState(true));
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        getFluidProvider().loadNBTData(tag.getCompoundTag(FLUID_NBT));
        getFluidProvider().setAdditionalConnection(connectsToAllSides());
    }

    @Override
    public String getCopiedDataIdentifier(EntityPlayer player) {
        return "apeiron.infiniteMEOutputAssembly";
    }

    @Override
    public NBTTagCompound getCopiedData(EntityPlayer player) {
        final NBTTagCompound result = super.getCopiedData(player);
        result.setTag(FLUID_NBT, getFluidProvider().getCopiedData(player));
        return result;
    }

    @Override
    public boolean pasteCopiedData(EntityPlayer player, NBTTagCompound tag) {
        if (!super.pasteCopiedData(player, tag)) return false;
        getFluidProvider().pasteCopiedData(player, tag.getCompoundTag(FLUID_NBT));
        getFluidProvider().setAdditionalConnection(connectsToAllSides());
        return true;
    }

    @Override
    public void writeToStream(ByteBuf buffer) {
        super.writeToStream(buffer);
        getFluidProvider().writeToClientPacket(buffer);
    }

    @Override
    public void readFromStream(ByteBuf buffer) {
        super.readFromStream(buffer);
        getFluidProvider().readFromClientPacket(buffer);
    }

    @Override
    public String[] getInfoData() {
        final List<String> lines = new ArrayList<>(Arrays.asList(super.getInfoData()));
        lines.addAll(Arrays.asList(fluidPort.getInfoData()));
        return lines.toArray(new String[0]);
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        final NBTTagCompound fluids = new NBTTagCompound();
        getFluidProvider().getWailaNBTData(player, tile, fluids, world, x, y, z);
        tag.setTag(FLUID_NBT, fluids);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getWailaAdvancedBody(ItemStack stack, List<String> lines, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaAdvancedBody(stack, lines, accessor, config);
        BigMEOutputProvider.WailaHelper.getWailaAdvancedBody(
            "fluid",
            lines,
            accessor.getNBTData()
                .getCompoundTag(FLUID_NBT));
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        return new InfiniteMEOutputAssemblyGui(this).build(data, sync, settings);
    }

    private final class FluidPort extends MTEBoundlessMEOutputHatch {

        private FluidPort() {
            super("apeiron.assembly_fluid_port", 4, new String[0], null);
            // GT's final base-tile setter assigns the tile's MTE. A delegated view keeps that assignment
            // local to this port and never replaces the real assembly in the world.
            final IGregTechTileEntity view = (IGregTechTileEntity) Proxy.newProxyInstance(
                IGregTechTileEntity.class.getClassLoader(),
                new Class<?>[] { IGregTechTileEntity.class },
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        if (method.getName()
                            .equals("equals")) return proxy == args[0];
                        if (method.getName()
                            .equals("hashCode")) return System.identityHashCode(proxy);
                        if (method.getName()
                            .equals("toString")) return "Apeiron assembly fluid view";
                    }
                    if (method.getName()
                        .equals("getMetaTileEntity")) return this;
                    if (method.getName()
                        .equals("setMetaTileEntity")) return null;
                    final IGregTechTileEntity base = MTEInfiniteMEOutputAssembly.this.getBaseMetaTileEntity();
                    if (base == null) {
                        if (method.getName()
                            .equals("isDead")) return true;
                        throw new IllegalStateException("Assembly fluid port is not attached to a tile");
                    }
                    try {
                        return method.invoke(base, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
            setBaseMetaTileEntity(view);
        }

        @Override
        protected BigMEOutputProvider<IAEFluidStack> createProvider() {
            return new BigMEOutputProvider<IAEFluidStack>(this) {

                @Override
                public AENetworkProxy getProxy() {
                    return MTEInfiniteMEOutputAssembly.this.getProxy();
                }
            };
        }

        @Override
        public boolean isValid() {
            return MTEInfiniteMEOutputAssembly.this.isValid();
        }

        @Override
        public ItemStack getCellStack() {
            return MTEInfiniteMEOutputAssembly.this.mInventory[1];
        }

        @Override
        public IGridProxyable getIGridProxyable() {
            return MTEInfiniteMEOutputAssembly.this;
        }

        @Override
        public ISaveProvider getISaveProvider() {
            return MTEInfiniteMEOutputAssembly.this;
        }

        @Override
        public BaseActionSource getActionSource() {
            return MTEInfiniteMEOutputAssembly.this.getActionSource();
        }

        @Override
        public ItemStack getVisual() {
            return MTEInfiniteMEOutputAssembly.this.getVisual();
        }

        @Override
        public void markDirty() {
            MTEInfiniteMEOutputAssembly.this.markDirty();
        }

        @Override
        public void notifyOutputSpaceChanged() {
            MTEInfiniteMEOutputAssembly.this.notifyOutputSpaceChanged();
        }

        @Override
        public EntityPlayer getLastClickedPlayer() {
            return MTEInfiniteMEOutputAssembly.this.getLastClickedPlayer();
        }
    }
}
