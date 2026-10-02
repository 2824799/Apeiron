package com.silvia.apeiron.ae.smoke;

import java.math.BigInteger;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.crafting.core.BigMECraftingInventory;
import com.silvia.apeiron.ae.crafting.core.UnlimitedCraftingCPU;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.stack.InfiniteAEStack;
import com.silvia.apeiron.ae.storage.BigMEInventories;
import com.silvia.apeiron.common.tile.crafting.TileInfiniteCraftingStorage;
import com.silvia.apeiron.common.tile.crafting.TileInfiniteCraftingUnit;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.WorldCoord;
import appeng.crafting.MECraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.storage.CreativeCellInventory;
import appeng.util.Platform;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import cpw.mods.fml.common.registry.GameRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public final class InfiniteCapabilitiesSmoke {

    private InfiniteCapabilitiesSmoke() {}

    public static void verify() {
        try {
            verifyDrops();
            ItemStack creative = AEApi.instance()
                .definitions()
                .items()
                .cellCreative()
                .maybeStack(1)
                .get();
            IAEStack<?> diamond = AEItemStack.create(new ItemStack(Items.diamond));
            verifyCell(creative, diamond);
            ItemStack fluidCell = new ItemStack(GameRegistry.findItem("ae2fc", "creative_fluid_storage"));
            verifyCell(fluidCell, AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)));
            CraftingCPUCluster cpu = new CraftingCPUCluster(new WorldCoord(0, 0, 0), new WorldCoord(0, 0, 1));
            java.lang.reflect.Method addTile = CraftingCPUCluster.class
                .getDeclaredMethod("addTile", appeng.tile.crafting.TileCraftingTile.class);
            addTile.setAccessible(true);
            addTile.invoke(cpu, new TileInfiniteCraftingUnit());
            addTile.invoke(cpu, new TileInfiniteCraftingStorage());
            check(
                ((UnlimitedCraftingCPU) cpu).isCraftingStorageUnlimited()
                    && ((UnlimitedCraftingCPU) cpu).isCraftingParallelUnlimited(),
                "CPU infinite capabilities");
            check(
                cpu.getAvailableStorage() == Long.MAX_VALUE && cpu.getCoProcessors() == Integer.MAX_VALUE,
                "CPU legacy projections");
            addTile.invoke(cpu, new TileInfiniteCraftingUnit());
            check(cpu.getCoProcessors() == Integer.MAX_VALUE, "two infinite coprocessors overflowed");
            verifyNetworkDiscovery(cpu);
            verifyCpuDisplay(cpu);
        } catch (ReflectiveOperationException | java.io.IOException error) {
            throw new IllegalStateException("Infinite capabilities verification failed", error);
        }
        Apeiron.LOG.info(
            "Infinite creative item/fluid supply, exact extraction, merge, copy, NBT, packets and CPU blocks verification passed");
    }

    private static void verifyCpuDisplay(CraftingCPUCluster cpu) throws java.io.IOException {
        final appeng.container.implementations.CraftingCPUStatus initial = new appeng.container.implementations.CraftingCPUStatus(
            cpu,
            1);
        check(
            "∞".equals(initial.formatCoprocessors()) && "∞".equals(initial.formatShorterCoprocessors()),
            "CPU labels still showed int max");
        final NBTTagCompound tag = new NBTTagCompound();
        initial.writeToNBT(tag);
        final BigInteger total = BigInteger.TEN.pow(60);
        com.silvia.apeiron.math.BigValueCodec
            .writeNBT(tag, "totalItems", "ApeironTotalItems", new com.silvia.apeiron.math.AdaptiveInteger(total));
        com.silvia.apeiron.math.BigValueCodec.writeNBT(
            tag,
            "remainingItems",
            "ApeironRemainingItems",
            new com.silvia.apeiron.math.AdaptiveInteger(
                total.multiply(BigInteger.valueOf(3))
                    .divide(BigInteger.valueOf(4))));
        final NBTTagCompound crafting = new NBTTagCompound();
        BigAEStackValues.copyWithSize(AEItemStack.create(new ItemStack(Items.emerald)), total)
            .writeToNBTGeneric(crafting);
        tag.setTag("crafting", crafting);
        final appeng.container.implementations.CraftingCPUStatus status = new appeng.container.implementations.CraftingCPUStatus(
            tag);
        final ByteBuf packet = Unpooled.buffer();
        try {
            status.writeToPacket(packet);
            final appeng.container.implementations.CraftingCPUStatus received = new appeng.container.implementations.CraftingCPUStatus(
                packet);
            check("∞".equals(received.formatShorterCoprocessors()), "CPU infinity flag lost in status packet");
            check(
                com.silvia.apeiron.ae.terminal.BigCpuProgressDisplay.fraction(received) == 0.25,
                "big CPU progress displayed zero");
            final String original = net.minecraft.util.EnumChatFormatting.GREEN
                + appeng.core.localization.GuiText.Progress.getLocal()
                + net.minecraft.util.EnumChatFormatting.RESET
                + ": 0 / "
                + Long.MAX_VALUE
                + " (0.00%)";
            final String tooltip = com.silvia.apeiron.ae.terminal.BigCpuProgressDisplay.tooltip(original, received);
            check(
                tooltip.contains(com.silvia.apeiron.math.BigNumberFormatter.formatExact(total))
                    && tooltip.contains(
                        com.silvia.apeiron.math.BigNumberFormatter.formatExact(total.divide(BigInteger.valueOf(4))))
                    && tooltip.contains("25.00%")
                    && !tooltip.contains(Long.toString(Long.MAX_VALUE)),
                "CPU tooltip truncated total/progress");
        } finally {
            packet.release();
        }
    }

    private static void verifyNetworkDiscovery(CraftingCPUCluster cpu) throws ReflectiveOperationException {
        TileInfiniteCraftingStorage storage = new TileInfiniteCraftingStorage();
        storage.updateStatus(cpu);
        appeng.api.networking.IGridNode node = (appeng.api.networking.IGridNode) java.lang.reflect.Proxy
            .newProxyInstance(
                appeng.api.networking.IGridNode.class.getClassLoader(),
                new Class<?>[] { appeng.api.networking.IGridNode.class },
                (proxy, method, args) -> method.getName()
                    .equals("getMachine") ? storage : null);
        appeng.api.networking.IMachineSet machines = (appeng.api.networking.IMachineSet) java.lang.reflect.Proxy
            .newProxyInstance(
                appeng.api.networking.IMachineSet.class.getClassLoader(),
                new Class<?>[] { appeng.api.networking.IMachineSet.class },
                (proxy, method, args) -> {
                    if (method.getName()
                        .equals("iterator"))
                        return java.util.Collections.singletonList(node)
                            .iterator();
                    if (method.getName()
                        .equals("getMachineClass")) return storage.getClass();
                    if (method.getName()
                        .equals("isEmpty")) return false;
                    if (method.getName()
                        .equals("size")) return 1;
                    if (method.getName()
                        .equals("contains")) return args[0] == node;
                    return null;
                });
        appeng.api.networking.IGrid grid = (appeng.api.networking.IGrid) java.lang.reflect.Proxy.newProxyInstance(
            appeng.api.networking.IGrid.class.getClassLoader(),
            new Class<?>[] { appeng.api.networking.IGrid.class },
            (proxy, method, args) -> {
                if (method.getName()
                    .equals("getMachinesClasses")) return new appeng.util.ReadOnlyCollection<>(storage.getClass());
                if (method.getName()
                    .equals("getMachines")) {
                    if (args[0] == storage.getClass()) return machines;
                    return java.lang.reflect.Proxy.newProxyInstance(
                        appeng.api.networking.IMachineSet.class.getClassLoader(),
                        new Class<?>[] { appeng.api.networking.IMachineSet.class },
                        (empty, call, values) -> {
                            if (call.getName()
                                .equals("iterator")) return java.util.Collections.emptyIterator();
                            if (call.getName()
                                .equals("getMachineClass")) return args[0];
                            if (call.getName()
                                .equals("isEmpty")) return true;
                            if (call.getName()
                                .equals("size")) return 0;
                            if (call.getName()
                                .equals("contains")) return false;
                            return null;
                        });
                }
                return null;
            });
        appeng.me.cache.CraftingGridCache cache = new appeng.me.cache.CraftingGridCache(grid);
        java.lang.reflect.Method update = appeng.me.cache.CraftingGridCache.class
            .getDeclaredMethod("updateCPUClusters");
        update.setAccessible(true);
        update.invoke(cache);
        java.lang.reflect.Field clusters = appeng.me.cache.CraftingGridCache.class
            .getDeclaredField("craftingCPUClusters");
        clusters.setAccessible(true);
        check(
            ((java.util.Set<?>) clusters.get(cache)).contains(cpu),
            "AE network scan did not discover infinite storage CPU");
        final Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        final java.lang.reflect.Field singleton = unsafeClass.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        final appeng.container.implementations.ContainerCraftConfirm confirm = (appeng.container.implementations.ContainerCraftConfirm) unsafeClass
            .getMethod("allocateInstance", Class.class)
            .invoke(singleton.get(null), appeng.container.implementations.ContainerCraftConfirm.class);
        confirm.getClass()
            .getField("apeiron$usedBytesExact")
            .set(
                confirm,
                BigInteger.TEN.pow(60)
                    .toString());
        check(
            confirm.cpuMatches(new appeng.container.implementations.CraftingCPUStatus(cpu, 0)),
            "unlimited CPU rejected exact byte requirement");
        CraftingCPUCluster finite = new CraftingCPUCluster(new WorldCoord(0, 0, 0), new WorldCoord(0, 0, 0));
        java.lang.reflect.Field capacity = CraftingCPUCluster.class.getDeclaredField("availableStorage");
        capacity.setAccessible(true);
        capacity.setLong(finite, Long.MAX_VALUE);
        check(
            !confirm.cpuMatches(new appeng.container.implementations.CraftingCPUStatus(finite, 1)),
            "finite CPU accepted saturated large byte requirement");
        for (net.minecraft.block.Block block : new net.minecraft.block.Block[] {
            com.silvia.apeiron.common.block.crafting.ApeironCraftingBlocks.unit,
            com.silvia.apeiron.common.block.crafting.ApeironCraftingBlocks.storage }) {
            ItemStack item = new ItemStack(block);
            check(item.getItem() instanceof appeng.block.AEBaseItemBlock, "CPU item bypassed AE renderer");
            check(
                net.minecraftforge.client.MinecraftForgeClient
                    .getItemRenderer(item, net.minecraftforge.client.IItemRenderer.ItemRenderType.INVENTORY)
                    == appeng.client.render.ItemRenderer.INSTANCE,
                "CPU inventory renderer missing");
        }
    }

    private static void verifyDrops() {
        com.silvia.apeiron.common.machine.block.ApeironMachineTile empty = new com.silvia.apeiron.common.machine.block.ApeironMachineTile();
        empty.setInitialValuesAsNBT(null, (short) com.silvia.apeiron.config.ApeironConfig.getMachineId(0));
        check(
            !empty.getDrops()
                .get(0)
                .hasTagCompound(),
            "empty output machine did not stack with new machines");
        com.silvia.apeiron.common.machine.block.ApeironMachineTile tile = new com.silvia.apeiron.common.machine.block.ApeironMachineTile();
        tile.setInitialValuesAsNBT(null, (short) com.silvia.apeiron.config.ApeironConfig.getMachineId(2));
        com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly machine = (com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly) tile
            .getMetaTileEntity();
        machine.mInventory[0] = AEApi.instance()
            .definitions()
            .items()
            .cell1k()
            .maybeStack(1)
            .get();
        machine.mInventory[1] = new ItemStack(GameRegistry.findItem("ae2fc", "creative_fluid_storage"));
        ((IStorageCell) machine.mInventory[0].getItem()).getConfigAEInventory(machine.mInventory[0])
            .putAEStackInSlot(0, AEItemStack.create(new ItemStack(Items.diamond)));
        ((IStorageCell) machine.mInventory[1].getItem()).getConfigAEInventory(machine.mInventory[1])
            .putAEStackInSlot(0, AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)));
        machine.getProvider()
            .addToCacheBig(
                BigAEStackValues
                    .copyWithSize(AEItemStack.create(new ItemStack(Items.diamond)), BigInteger.TEN.pow(60)));
        ItemStack drop = tile.getDrops()
            .get(0);
        check(
            drop.hasTagCompound() && !machine.shouldDropItemAt(0) && !machine.shouldDropItemAt(1),
            "output storage cells duplicated on dismantling");
        check(
            drop.getTagCompound()
                .getCompoundTag("ApeironContents")
                .hasKey("items"),
            "filled drop missing content tooltip");
        com.silvia.apeiron.common.machine.block.ApeironMachineTile restored = new com.silvia.apeiron.common.machine.block.ApeironMachineTile();
        restored.setInitialValuesAsNBT(
            drop.getTagCompound(),
            (short) com.silvia.apeiron.config.ApeironConfig.getMachineId(2));
        com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly loaded = (com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly) restored
            .getMetaTileEntity();
        check(
            ItemStack.areItemStacksEqual(machine.mInventory[0], loaded.mInventory[0])
                && ItemStack.areItemStacksEqual(machine.mInventory[1], loaded.mInventory[1]),
            "dropped output assembly lost installed item/fluid cells: original="
                + java.util.Arrays.toString(machine.mInventory)
                + " loaded="
                + java.util.Arrays.toString(loaded.mInventory)
                + " saved="
                + drop.getTagCompound()
                    .getTag("Inventory"));
        check(
            loaded.getProvider()
                .getCachedAmountBig()
                .equals(BigInteger.TEN.pow(60)),
            "drop lost exact buffered output");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void verifyCell(ItemStack cell, IAEStack<?> type) throws java.io.IOException {
        ((IStorageCell) cell.getItem()).getConfigAEInventory(cell)
            .putAEStackInSlot(0, (IAEStack) type);
        IMEInventory inventory = new CreativeCellInventory(cell);
        IItemList list = type.getStackType()
            .createList();
        inventory.getAvailableItems(list);
        IAEStack<?> advertised = list.findPrecise(type);
        check(
            BigAEStackValues.isInfinite(advertised) && advertised.isMeaningful(),
            "creative supply did not advertise infinity");
        for (Actionable mode : Actionable.values()) {
            IAEStack<?> request = BigAEStackValues.copyWithSize(type, BigInteger.TEN.pow(600));
            IAEStack<?> extracted = BigMEInventories.extractItemsBig(inventory, request, mode, null);
            check(
                BigAEStackValues.get(extracted)
                    .equals(BigInteger.TEN.pow(600)) && !BigAEStackValues.isInfinite(extracted),
                "creative extraction capped or returned renewable physical items");
        }
        check(BigAEStackValues.isInfinite(advertised.copy()), "copy lost infinity");
        IAEStack<?> combined = type.copy();
        ((IAEStack) combined).add((IAEStack) advertised);
        check(
            BigAEStackValues.isInfinite(combined),
            "merge lost infinity for " + combined.getClass()
                .getName() + " source=" + BigAEStackValues.isInfinite(advertised));
        combined.decStackSize(1);
        check(BigAEStackValues.isInfinite(combined), "merge or decrement lost infinity");
        NBTTagCompound tag = new NBTTagCompound();
        combined.writeToNBT(tag);
        IAEStack<?> restored = type instanceof appeng.api.storage.data.IAEFluidStack
            ? AEFluidStack.loadFluidStackFromNBT(tag)
            : AEItemStack.loadItemStackFromNBT(tag);
        check(BigAEStackValues.isInfinite(restored), "NBT lost infinity");
        ByteBuf wire = Unpooled.buffer();
        try {
            Platform.writeStackByte(combined, wire);
            check(
                BigAEStackValues.isInfinite(Platform.readStackByte(wire)) && wire.readableBytes() == 0,
                "packet lost infinity or left trailing bytes");
        } finally {
            wire.release();
        }
        MECraftingInventory model = new MECraftingInventory();
        model.injectItems(combined, Actionable.MODULATE);
        IAEStack<?> request = BigAEStackValues.copyWithSize(type, BigInteger.TEN.pow(600));
        for (int i = 0; i < 2; i++) check(
            BigAEStackValues.get(((BigMECraftingInventory) model).extractItemsBig(request, Actionable.MODULATE))
                .equals(BigInteger.TEN.pow(600)),
            "crafting simulation consumed infinite supply");
        IAEStack<?> finite = BigAEStackValues.copyWithSize(combined, BigInteger.ONE);
        check(
            !BigAEStackValues.isInfinite(finite) && BigAEStackValues.get(finite)
                .equals(BigInteger.ONE),
            "finite request inherited infinity");
        ((InfiniteAEStack) combined).setInfinite(true);
        combined.reset();
        check(!BigAEStackValues.isInfinite(combined), "reset retained infinity");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
