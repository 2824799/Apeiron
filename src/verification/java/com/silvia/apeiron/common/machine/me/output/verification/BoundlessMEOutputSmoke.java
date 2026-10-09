package com.silvia.apeiron.common.machine.me.output.verification;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.automation.BigPoweredTransfers;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigMEInventories;
import com.silvia.apeiron.ae.storage.BigMEInventory;
import com.silvia.apeiron.ae.storage.BigStorageCell;
import com.silvia.apeiron.api.machine.me.output.BigFluidOutputTransaction;
import com.silvia.apeiron.api.machine.me.output.BigItemOutputTransaction;
import com.silvia.apeiron.api.machine.me.output.BigOutputTransaction;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputBus;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputHatch;
import com.silvia.apeiron.common.machine.me.output.storage.BigMEOutputProvider;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.items.materials.MaterialType;
import appeng.items.storage.ItemBasicStorageCell;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEFluidStackType;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Opt-in checks in the real transformed client; never edits the player's world or registered prototypes. */
public final class BoundlessMEOutputSmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(180)
        .add(BigInteger.valueOf(17));
    private static final BaseActionSource SOURCE = new BaseActionSource();

    private BoundlessMEOutputSmoke() {}

    public static void verify() {
        com.silvia.apeiron.common.machine.block.verification.MachineDropsSmoke.verify();
        check(GameRegistry.findBlock(Apeiron.MODID, "machines") == ApeironMachines.block, "own block registry");
        check(GregTechAPI.METATILEENTITIES[ApeironConfig.getMachineId(0)] == ApeironMachines.itemOutputBus, "item ID");
        check(
            GregTechAPI.METATILEENTITIES[ApeironConfig.getMachineId(1)] == ApeironMachines.fluidOutputHatch,
            "fluid ID");
        ApeironMachines.validateRegisteredReservation();
        verifyItems();
        verifyFluids();
        verifyStorageCell(false);
        verifyStorageCell(true);
        verifyUnlimitedBuffer(false);
        verifyUnlimitedBuffer(true);
        if (cpw.mods.fml.common.FMLCommonHandler.instance()
            .getSide()
            .isClient()) verifyCreativeEntries();
        verifyPoweredTransfer();
        Apeiron.LOG.info("Apeiron ME output blocks, ID reservation and big-count runtime verification passed");
    }

    private static ApeironMachineTile tile(final int offset) {
        final ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(offset));
        check(tile.getMetaTileEntity() != null, "machine clone");
        return tile;
    }

    private static IAEItemStack diamonds(final BigInteger amount) {
        return BigAEStackValues.copyWithSize(AEItemStack.create(new ItemStack(Items.diamond)), amount);
    }

    private static IAEFluidStack water(final BigInteger amount) {
        return BigAEStackValues.copyWithSize(AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)), amount);
    }

    private static void verifyItems() {
        final ApeironMachineTile tile = tile(0);
        final MTEBoundlessMEOutputBus bus = (MTEBoundlessMEOutputBus) tile.getMetaTileEntity();
        final BigMEOutputProvider<IAEItemStack> provider = bus.getProvider();
        check(
            provider.isCacheUnlimited() && provider.getCacheCapacity() == Long.MAX_VALUE,
            "item unlimited cache and legacy view");
        provider.setCheckMode(true);
        final IAEItemStack simulated = diamonds(HUGE);
        check(bus.storePartialBig(simulated, true), "item simulation");
        check(
            provider.getCachedAmountBig()
                .signum() == 0,
            "item simulation mutated cache");
        final IAEItemStack real = diamonds(HUGE);
        check(
            bus.storePartialBig(real, false) && BigAEStackValues.get(real)
                .signum() == 0,
            "item insertion");
        check(
            provider.getCachedAmountBig()
                .equals(HUGE),
            "item exact cache count");
        check(provider.getCachedAmount() == Long.MAX_VALUE, "item legacy count saturation");
        final List<IAEItemStack> visible = provider.getCacheList();
        BigAEStackValues.set(visible.get(0), BigInteger.ONE);
        check(
            provider.getCachedAmountBig(diamonds(BigInteger.ONE))
                .equals(HUGE),
            "cache view aliases stored amount");
        final BigItemOutputTransaction transaction = bus.createTransactionBig();
        final IAEItemStack staged = diamonds(HUGE);
        check(transaction.storePartialBig(staged, BigInteger.ONE, BigInteger.ONE), "big item transaction");
        check(
            provider.getCachedAmountBig()
                .equals(HUGE),
            "uncommitted transaction mutated provider");
        transaction.commit();
        final BigInteger stored = HUGE.multiply(BigInteger.valueOf(2));
        check(
            provider.getCachedAmountBig()
                .equals(stored),
            "transaction commit count");
        try {
            transaction.commit();
            throw new IllegalStateException("double transaction commit accepted");
        } catch (IllegalStateException expected) {
            check(
                provider.getCachedAmountBig()
                    .equals(stored),
                "double commit duplicated outputs");
        }
        final ItemStack physical = new ItemStack(Items.diamond, 64);
        check(bus.storePartial(physical, true) && physical.stackSize == 0, "legacy item simulation");
        check(
            provider.getCachedAmountBig()
                .equals(stored),
            "legacy simulation changed cache");
        check(
            bus.storePartialBig(diamonds(HUGE.multiply(BigInteger.valueOf(500))), true),
            "populated item cache restricted larger output");
        final NBTTagCompound saved = new NBTTagCompound();
        bus.saveNBTData(saved);
        final MTEBoundlessMEOutputBus restored = (MTEBoundlessMEOutputBus) tile(0).getMetaTileEntity();
        restored.loadNBTData(saved);
        check(
            restored.getProvider()
                .getCachedAmountBig()
                .equals(stored),
            "item NBT count");
        restored.loadNBTData(saved);
        check(
            restored.getProvider()
                .getCachedAmountBig()
                .equals(stored),
            "reloading duplicated item counts");
        verifyUnlimitedPacket(provider, restored.getProvider());
        verifyDrop(tile, stored, 0);
        check(
            ApeironMachines.itemOutputBus.getProvider()
                .getCachedAmountBig()
                .signum() == 0,
            "item prototype modified");
    }

    private static void verifyFluids() {
        final ApeironMachineTile tile = tile(1);
        final MTEBoundlessMEOutputHatch hatch = (MTEBoundlessMEOutputHatch) tile.getMetaTileEntity();
        final BigMEOutputProvider<IAEFluidStack> provider = hatch.getProvider();
        check(
            provider.isCacheUnlimited() && provider.getCacheCapacity() == Long.MAX_VALUE,
            "fluid unlimited cache and legacy view");
        provider.setCheckMode(true);
        check(
            hatch.fillBig(new FluidStack(FluidRegistry.WATER, 1), HUGE, false)
                .equals(HUGE),
            "fluid simulation");
        check(
            provider.getCachedAmountBig()
                .signum() == 0,
            "fluid simulation changed cache");
        check(
            hatch.fillBig(new FluidStack(FluidRegistry.WATER, 1), HUGE, true)
                .equals(HUGE),
            "fluid insertion");
        final BigFluidOutputTransaction transaction = hatch.createTransactionBig();
        check(transaction.storePartialBig(water(HUGE), BigInteger.ONE, BigInteger.ONE), "big fluid transaction");
        transaction.commit();
        final BigInteger stored = HUGE.multiply(BigInteger.valueOf(2));
        check(
            provider.getCachedAmountBig()
                .equals(stored),
            "fluid transaction commit count");
        check(hatch.fill(new FluidStack(FluidRegistry.WATER, 250), false) == 250, "legacy fluid simulation");
        check(
            provider.getCachedAmountBig()
                .equals(stored),
            "legacy fluid simulation changed cache");
        final NBTTagCompound saved = new NBTTagCompound();
        hatch.saveNBTData(saved);
        final MTEBoundlessMEOutputHatch restored = (MTEBoundlessMEOutputHatch) tile(1).getMetaTileEntity();
        restored.loadNBTData(saved);
        check(
            restored.getProvider()
                .getCachedAmountBig()
                .equals(stored),
            "fluid NBT count");
        verifyUnlimitedPacket(provider, restored.getProvider());
        verifyDrop(tile, stored, 1);
        check(
            ApeironMachines.fluidOutputHatch.getProvider()
                .getCachedAmountBig()
                .signum() == 0,
            "fluid prototype modified");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void verifyStorageCell(final boolean fluid) {
        final VerificationCell item = new VerificationCell(fluid);
        final ItemStack cellStack = new ItemStack(item);
        final IAEStack<?> accepted = fluid ? water(HUGE) : diamonds(HUGE);
        item.getConfigAEInventory(cellStack)
            .putAEStackInSlot(0, BigAEStackValues.copyWithSize(accepted, BigInteger.ONE));
        final StorageChannel channel = fluid ? StorageChannel.FLUIDS : StorageChannel.ITEMS;
        final IMEInventoryHandler inventory = AEApi.instance()
            .registries()
            .cell()
            .getCellInventory(cellStack, null, channel);
        check(inventory != null, "cell handler registration");
        check(
            BigMEInventories.injectItemsBig(inventory, accepted.copy(), Actionable.MODULATE, SOURCE) == null,
            "preload exact cell contents");
        final ApeironMachineTile tile = tile(fluid ? 1 : 0);
        final BigMEOutputProvider provider;
        if (fluid) {
            final MTEBoundlessMEOutputHatch hatch = (MTEBoundlessMEOutputHatch) tile.getMetaTileEntity();
            hatch.mInventory[0] = cellStack;
            provider = hatch.getProvider();
        } else {
            final MTEBoundlessMEOutputBus bus = (MTEBoundlessMEOutputBus) tile.getMetaTileEntity();
            bus.mInventory[0] = cellStack;
            provider = bus.getProvider();
        }
        provider.onContentsChanged(0);
        provider.setCacheMode(true);
        provider.setCheckMode(true);
        check(provider.isFiltered(), "cell partition filtering");
        check(provider.isCacheUnlimited(), "inserted cell limited local buffer");
        check(provider.storePartialBig(accepted.copy(), true), "cell simulation rejected huge amount");
        check(
            provider.getCachedAmountBig()
                .signum() == 0,
            "cell simulation mutated output cache");
        final IAEStack<?> rejected = fluid
            ? BigAEStackValues.copyWithSize(AEFluidStack.create(new FluidStack(FluidRegistry.LAVA, 1)), HUGE)
            : BigAEStackValues.copyWithSize(AEItemStack.create(new ItemStack(Items.emerald)), HUGE);
        check(!provider.storePartialBig(rejected, false), "partition accepted wrong output type");
        check(
            BigAEStackValues.get(rejected)
                .equals(HUGE),
            "filter changed rejected count");
        if (fluid) {
            final BigFluidOutputTransaction transaction = ((MTEBoundlessMEOutputHatch) tile.getMetaTileEntity())
                .createTransactionBig();
            check(transaction.storePartialBig(water(HUGE), BigInteger.ONE, BigInteger.ONE), "cell fluid transaction");
            transaction.commit();
        } else {
            final BigItemOutputTransaction transaction = ((MTEBoundlessMEOutputBus) tile.getMetaTileEntity())
                .createTransactionBig();
            check(transaction.storePartialBig(diamonds(HUGE), BigInteger.ONE, BigInteger.ONE), "cell item transaction");
            transaction.commit();
        }
        check(
            provider.getCachedAmountBig()
                .equals(HUGE),
            "cell transaction lost quantity");
        provider.setCacheMode(false);
        provider.cellToCacheTransfer();
        check(
            provider.getCachedAmountBig()
                .equals(HUGE.multiply(BigInteger.valueOf(2))),
            "cell-to-cache transfer lost quantity");
    }

    private static void verifyCreativeEntries() {
        final List<ItemStack> entries = new java.util.ArrayList<>();
        ApeironMachines.block
            .getSubBlocks(Item.getItemFromBlock(ApeironMachines.block), ApeironMachines.CREATIVE_TAB, entries);
        final int[] expectedOffsets = { ApeironMachines.ITEM_OUTPUT_BUS_OFFSET,
            ApeironMachines.FLUID_OUTPUT_HATCH_OFFSET, ApeironMachines.MIXED_OUTPUT_ASSEMBLY_OFFSET,
            ApeironMachines.INFINITE_ENERGY_HATCH_OFFSET, ApeironMachines.PATTERN_INPUT_ASSEMBLY_OFFSET,
            ApeironMachines.PATTERN_INPUT_MIRROR_OFFSET, ApeironMachines.CIRCUIT_PROVIDER_OFFSET,
            ApeironMachines.STORAGE_INPUT_BUS_OFFSET, ApeironMachines.STORAGE_INPUT_HATCH_OFFSET,
            ApeironMachines.STORAGE_INPUT_ASSEMBLY_OFFSET, ApeironMachines.ULTIMATE_ENERGY_HATCH_OFFSET,
            ApeironMachines.EYE_OF_HARMONY_ENHANCEMENT_OFFSET, ApeironMachines.QUANTUM_ENHANCEMENT_OFFSET,
            ApeironMachines.AUTO_LASER_BEAMLINE_INPUT_OFFSET, ApeironMachines.ME_BEAMLINE_OUTPUT_OFFSET };
        check(
            entries.size() == expectedOffsets.length,
            "creative and NEI enumeration has missing or duplicate machines");
        for (int offset : expectedOffsets) {
            final int id = ApeironConfig.getMachineId(offset);
            check(
                entries.stream()
                    .anyMatch(
                        stack -> stack.getItemDamage() == id
                            && stack.getItem() == Item.getItemFromBlock(ApeironMachines.block)),
                "missing creative output ID " + id);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void verifyUnlimitedBuffer(boolean fluid) {
        final VerificationCell cellItem = new VerificationCell(fluid, true);
        final ItemStack cellStack = new ItemStack(cellItem);
        final IAEStack<?> type = fluid ? water(BigInteger.ONE) : diamonds(BigInteger.ONE);
        cellItem.getConfigAEInventory(cellStack)
            .putAEStackInSlot(0, type);
        final StorageChannel channel = fluid ? StorageChannel.FLUIDS : StorageChannel.ITEMS;
        final IMEInventoryHandler cell = AEApi.instance()
            .registries()
            .cell()
            .getCellInventory(cellStack, null, channel);
        check(
            BigMEInventories.injectItemsBig(cell, type.copy(), Actionable.MODULATE, SOURCE) == null,
            "finite cell preload");
        final ApeironMachineTile tile = tile(fluid ? 1 : 0);
        final BigMEOutputProvider provider = fluid
            ? ((MTEBoundlessMEOutputHatch) tile.getMetaTileEntity()).getProvider()
            : ((MTEBoundlessMEOutputBus) tile.getMetaTileEntity()).getProvider();
        ((gregtech.api.metatileentity.MetaTileEntity) tile.getMetaTileEntity()).mInventory[0] = cellStack;
        provider.onContentsChanged(0);
        provider.setCacheMode(true);
        final NBTTagCompound legacy = new NBTTagCompound();
        provider.saveNBTData(legacy);
        legacy.setLong("baseCapacity", 1L);
        legacy.setByteArray("ApeironBaseCapacity", BigInteger.ONE.toByteArray());
        legacy.setBoolean("checkMode", true);
        provider.loadNBTData(legacy);
        final BigInteger enormous = BigInteger.TEN.pow(600)
            .add(BigInteger.valueOf(19));
        check(
            provider.storePartialBig(BigAEStackValues.copyWithSize(type, enormous), true),
            "finite cell or legacy settings limited simulation");
        check(
            provider.getCachedAmountBig()
                .signum() == 0,
            "unlimited simulation mutated buffer");
        final BigOutputTransaction transaction = fluid
            ? ((MTEBoundlessMEOutputHatch) tile.getMetaTileEntity()).createTransactionBig()
            : ((MTEBoundlessMEOutputBus) tile.getMetaTileEntity()).createTransactionBig();
        final IAEStack<?> staged = BigAEStackValues.copyWithSize(type, enormous);
        if (fluid) ((BigFluidOutputTransaction) transaction)
            .storePartialBig((IAEFluidStack) staged, BigInteger.ONE, BigInteger.ONE);
        else((BigItemOutputTransaction) transaction)
            .storePartialBig((IAEItemStack) staged, BigInteger.ONE, BigInteger.ONE);
        check(
            BigAEStackValues.get(staged)
                .signum() == 0,
            "unlimited transaction rejected amount");
        check(
            provider.getCachedAmountBig()
                .signum() == 0,
            "preflight modified live buffer");
        final IAEStack<?> stillStored = BigMEInventories
            .extractItemsBig(cell, type.copy(), Actionable.SIMULATE, SOURCE);
        check(
            BigAEStackValues.get(stillStored)
                .equals(BigInteger.ONE),
            "preflight modified real finite cell");
        transaction.commit();
        check(
            provider.storePartialBig(BigAEStackValues.copyWithSize(type, enormous), false),
            "populated buffer rejected second enormous amount");
        check(
            provider.getCachedAmountBig()
                .equals(enormous.multiply(BigInteger.valueOf(2))),
            "unlimited transaction or accumulation lost quantity");
        provider.saveNBTData(legacy);
        check(
            !legacy.hasKey("baseCapacity") && !legacy.hasKey("ApeironBaseCapacity") && !legacy.hasKey("checkMode"),
            "obsolete capacity settings persisted");
        provider.loadNBTData(legacy);
        check(
            provider.getCachedAmountBig()
                .equals(enormous.multiply(BigInteger.valueOf(2))),
            "unlimited buffer NBT lost quantities beyond double range");
    }

    private static final class VerificationCell extends ItemBasicStorageCell implements BigStorageCell {

        private final boolean fluid;
        private final boolean finite;

        private VerificationCell(final boolean fluid) {
            this(fluid, false);
        }

        private VerificationCell(final boolean fluid, final boolean finite) {
            super(MaterialType.Cell1kPart, 1L);
            this.fluid = fluid;
            this.finite = finite;
        }

        @Override
        public IAEStackType<?> getStackType() {
            return fluid ? AEFluidStackType.FLUID_STACK_TYPE : AEItemStackType.ITEM_STACK_TYPE;
        }

        @Override
        public BigInteger getBytesBig(final ItemStack cell) {
            return finite ? BigInteger.valueOf(1024) : HUGE.multiply(BigInteger.valueOf(10));
        }

        @Override
        public long getBytesLong(final ItemStack cell) {
            return finite ? 1024 : Long.MAX_VALUE;
        }
    }

    private static void verifyUnlimitedPacket(final BigMEOutputProvider<?> source,
        final BigMEOutputProvider<?> target) {
        final ByteBuf packet = Unpooled.buffer();
        try {
            source.writeToClientPacket(packet);
            target.readFromClientPacket(packet);
            check(source.isCacheUnlimited() && target.isCacheUnlimited(), "unlimited buffer marker packet");
            check(!packet.isReadable(), "unlimited buffer packet not fully read");
        } finally {
            packet.release();
        }
    }

    private static void verifyDrop(final ApeironMachineTile tile, final BigInteger stored, final int offset) {
        final ItemStack dropped = tile.getDrops()
            .get(0);
        check(dropped.getItem() == Item.getItemFromBlock(ApeironMachines.block), "drop changed registry");
        check(dropped.getItemDamage() == ApeironConfig.getMachineId(offset), "drop changed metadata");
        final ApeironMachineTile replaced = new ApeironMachineTile();
        replaced.setInitialValuesAsNBT(dropped.getTagCompound(), (short) dropped.getItemDamage());
        final BigMEOutputProvider<?> provider = offset == 0
            ? ((MTEBoundlessMEOutputBus) replaced.getMetaTileEntity()).getProvider()
            : ((MTEBoundlessMEOutputHatch) replaced.getMetaTileEntity()).getProvider();
        check(
            provider.getCachedAmountBig()
                .equals(stored),
            "drop/replacement lost exact counts");
        check(provider.isCacheUnlimited(), "replacement lost unlimited buffer");
    }

    @SuppressWarnings("rawtypes")
    private static void verifyPoweredTransfer() {
        final BigInteger[] free = { HUGE };
        final int[] mutations = { 0 };
        final IMEInventory storage = (IMEInventory) Proxy.newProxyInstance(
            BoundlessMEOutputSmoke.class.getClassLoader(),
            new Class<?>[] { IMEInventory.class, BigMEInventory.class },
            (proxy, method, args) -> {
                if (method.getName()
                    .equals("injectItemsBig")) {
                    final IAEStack<?> input = (IAEStack<?>) args[0];
                    final BigInteger requested = BigAEStackValues.get(input);
                    final BigInteger inserted = free[0].min(requested);
                    if (args[1] == Actionable.MODULATE) {
                        free[0] = free[0].subtract(inserted);
                        mutations[0]++;
                    }
                    return requested.equals(inserted) ? null
                        : BigAEStackValues.copyWithSize(input, requested.subtract(inserted));
                }
                throw new UnsupportedOperationException(method.getName());
            });
        final double[] budget = { 1234 };
        final IEnergySource energy = (request, mode, multiplier) -> {
            final double available = Math.min(request, budget[0]);
            if (mode == Actionable.MODULATE) budget[0] -= available;
            return available;
        };
        IAEStack<?> rest = BigPoweredTransfers
            .poweredInsertBulkBig(energy, storage, diamonds(HUGE), SOURCE, Actionable.SIMULATE);
        check(
            BigAEStackValues.get(rest)
                .equals(HUGE.subtract(BigInteger.valueOf(1234))),
            "finite-power simulation remainder");
        check(free[0].equals(HUGE) && mutations[0] == 0 && budget[0] == 1234, "powered simulation mutated state");
        rest = BigPoweredTransfers.poweredInsertBulkBig(energy, storage, diamonds(HUGE), SOURCE, Actionable.MODULATE);
        check(
            BigAEStackValues.get(rest)
                .equals(HUGE.subtract(BigInteger.valueOf(1234))),
            "finite-power actual remainder");
        check(
            free[0].equals(BigAEStackValues.get(rest)) && mutations[0] == 1 && budget[0] == 0,
            "powered transfer conservation");
        rest = BigPoweredTransfers.poweredInsertBulkBig(energy, storage, diamonds(HUGE), SOURCE, Actionable.MODULATE);
        check(
            BigAEStackValues.get(rest)
                .equals(HUGE) && mutations[0] == 1,
            "zero power allowed insertion");
        free[0] = HUGE;
        final IEnergySource unlimited = (request, mode, multiplier) -> Double.POSITIVE_INFINITY;
        check(
            BigPoweredTransfers.poweredInsertBulkBig(unlimited, storage, diamonds(HUGE), SOURCE, Actionable.MODULATE)
                == null,
            "bulk transfer failed on huge amounts");
        check(free[0].signum() == 0 && mutations[0] == 2, "bulk transfer iterated over physical chunks");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new IllegalStateException("Apeiron ME output verification: " + message);
    }
}
