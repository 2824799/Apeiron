package com.silvia.apeiron.common.machine.me.stocking.verification;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.stack.InfiniteAEStack;
import com.silvia.apeiron.ae.storage.BigMEInventory;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.me.circuit.MTEInfiniteProgrammingCircuitProvider;
import com.silvia.apeiron.common.machine.me.stocking.StockingInputLogic;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;

import appeng.api.config.Actionable;
import appeng.api.config.PowerUnits;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import appeng.util.item.ItemList;
import cpw.mods.fml.common.Loader;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/** Detached, transformed AE inventories and recipe projections; does not open a GUI or touch player inventories. */
public final class StockingInputsSmoke {

    private StockingInputsSmoke() {}

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Stocking verification: " + message);
    }

    private static IAEStack<?> item() {
        return AEItemStack.create(new ItemStack(Items.diamond));
    }

    private static IAEStack<?> fluid() {
        return AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1));
    }

    private static IAEStack<?> amount(IAEStack<?> type, BigInteger amount) {
        IAEStack<?> result = type.copy();
        if (result instanceof InfiniteAEStack) ((InfiniteAEStack) result).setInfinite(false);
        return BigAEStackValues.set(result, amount);
    }

    public static void verify() {
        com.silvia.apeiron.common.network.verification.GuiSnapshotSmoke.verify();
        ApeironMachines.validateRegisteredReservation();
        check(
            ApeironMachines.storageInputBus.getStockingInput()
                .getKind() == StockingInputLogic.Kind.ITEMS,
            "bus kind");
        check(
            ApeironMachines.storageInputHatch.getStockingInput()
                .getKind() == StockingInputLogic.Kind.FLUIDS,
            "hatch kind");
        check(
            ApeironMachines.storageInputAssembly.getStockingInput()
                .getKind() == StockingInputLogic.Kind.MIXED,
            "assembly kind");
        check(
            !ApeironMachines.storageInputBus.isValidSlot(1) && !ApeironMachines.storageInputBus.allowPullStack(
                null,
                1,
                net.minecraftforge.common.util.ForgeDirection.NORTH,
                new ItemStack(Items.diamond)),
            "virtual network view leaked into hopper extraction");
        verifyLegacyAndBigDebits();
        verifyRecipeIntegration();
        verifyAtomicRollback();
        verifySelectionsAndPersistence();
        if (Loader.isModLoaded("programmablehatches")) verifyProvider();
        Apeiron.LOG.info(
            "Stocking input verification passed: 360 mixed selections, exact recipe debits, atomic rollback, persisted refunds and infinite circuit templates");
    }

    private static void verifyLegacyAndBigDebits() {
        BigInteger total = BigInteger.TEN.pow(70);
        Network network = new Network();
        network.store(item(), total);
        network.store(fluid(), total);
        Input input = new Input(network, StockingInputLogic.Kind.MIXED);
        check(input.setMark(0, item()) && input.setMark(359, fluid()), "mixed marks");
        input.begin();
        check(
            network.count(item())
                .equals(total),
            "simulation withdrew items");
        input.itemView(0).stackSize -= 7;
        input.fluidViews()[0].amount -= 13;
        check(
            input.end()
                .wasSuccessful(),
            "legacy debit failed");
        check(
            network.count(item())
                .equals(total.subtract(BigInteger.valueOf(7)))
                && network.count(fluid())
                    .equals(total.subtract(BigInteger.valueOf(13))),
            "legacy debit was truncated or repeated");
        input.begin();
        BigInteger huge = BigInteger.TEN.pow(60);
        check(
            StockingInputLogic
                .commit(Collections.singletonMap(input, Arrays.asList(amount(item(), huge), amount(fluid(), huge)))),
            "exact commit failed");
        input.recordCommitted(0, huge);
        input.recordCommitted(359, huge);
        check(
            input.end()
                .wasSuccessful(),
            "exact completion failed");
        check(
            network.count(item())
                .equals(
                    total.subtract(BigInteger.valueOf(7))
                        .subtract(huge)),
            "exact debit charged twice");
        check(
            network.count(fluid())
                .equals(
                    total.subtract(BigInteger.valueOf(13))
                        .subtract(huge)),
            "exact fluid debit charged twice");
        check(
            input.end()
                .wasSuccessful()
                && network.count(item())
                    .equals(
                        total.subtract(BigInteger.valueOf(7))
                            .subtract(huge)),
            "repeated end charged again");
    }

    private static final class Assembly
        extends com.silvia.apeiron.common.machine.me.stocking.MTEInfiniteStorageInputAssembly {

        private final Input input;

        private Assembly(Input input) {
            super("apeiron.verification.stocking_assembly", 6, new String[0], null);
            this.input = input;
        }

        @Override
        public StockingInputLogic getStockingInput() {
            return input;
        }
    }

    private static void verifyRecipeIntegration() {
        BigInteger total = BigInteger.TEN.pow(80), parallels = BigInteger.TEN.pow(60);
        Network network = new Network();
        network.store(item(), total);
        network.store(fluid(), total);
        Input input = new Input(network, StockingInputLogic.Kind.MIXED);
        input.setMark(0, item());
        input.setMark(359, fluid());
        input.begin();
        gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace controller = new gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace(
            "apeiron.verification.stocking_controller");
        controller.mDualInputHatches.add(new Assembly(input));
        gregtech.api.util.GTRecipe recipe = new gregtech.api.util.GTRecipe(
            false,
            new ItemStack[] { new ItemStack(Items.diamond, 2) },
            new ItemStack[] { new ItemStack(Items.emerald) },
            null,
            null,
            null,
            null,
            null,
            new FluidStack[] { new FluidStack(FluidRegistry.WATER, 3) },
            null,
            20,
            8,
            0);
        com.silvia.apeiron.common.machine.parallel.BigRecipeInputs inputs = new com.silvia.apeiron.common.machine.parallel.BigRecipeInputs(
            controller,
            recipe,
            input.itemViews(),
            input.fluidViews());
        check(
            inputs.allocation()
                .maximum(com.silvia.apeiron.api.machine.parallel.ParallelLimit.bounded(parallels))
                .equals(parallels),
            "real recipe allocation narrowed the ME counts");
        inputs.consume(parallels);
        check(
            input.end()
                .wasSuccessful(),
            "real recipe completion failed");
        check(
            network.count(item())
                .equals(total.subtract(parallels.multiply(BigInteger.valueOf(2))))
                && network.count(fluid())
                    .equals(total.subtract(parallels.multiply(BigInteger.valueOf(3)))),
            "real recipe debit was not exact");
    }

    private static void verifyAtomicRollback() {
        BigInteger total = BigInteger.TEN.pow(50);
        Network network = new Network();
        network.store(item(), total);
        network.store(fluid(), total);
        Input items = new Input(network, StockingInputLogic.Kind.ITEMS),
            fluids = new Input(network, StockingInputLogic.Kind.FLUIDS);
        items.setMark(0, item());
        fluids.setMark(0, fluid());
        items.begin();
        fluids.begin();
        items.itemView(0).stackSize -= 10;
        fluids.fluidViews()[0].amount -= 6;
        network.partialFluid = true;
        check(
            !StockingInputLogic.finishGroup(Arrays.asList(items, fluids))
                .wasSuccessful(),
            "partial transfer accepted");
        check(
            network.count(item())
                .equals(total)
                && network.count(fluid())
                    .equals(total),
            "partial transfer lost material");
        Input mixed = new Input(network, StockingInputLogic.Kind.MIXED);
        network.partialFluid = true;
        network.rejectInsert = true;
        check(
            !StockingInputLogic.commit(
                Collections.singletonMap(
                    mixed,
                    Arrays.asList(amount(item(), BigInteger.TEN), amount(fluid(), BigInteger.valueOf(6))))),
            "partial transfer accepted with rejected refund");
        check(
            mixed.getRefundAmount(false)
                .equals(BigInteger.TEN)
                && mixed.getRefundAmount(true)
                    .equals(BigInteger.valueOf(3)),
            "rejected refund not retained");
        NBTTagCompound tag = new NBTTagCompound();
        mixed.save(tag);
        Input restored = new Input(network, StockingInputLogic.Kind.MIXED);
        restored.load(tag);
        check(
            restored.getRefundAmount(false)
                .equals(BigInteger.TEN)
                && restored.getRefundAmount(true)
                    .equals(BigInteger.valueOf(3)),
            "refund lost in NBT");
        check(
            !restored.copyConfiguration()
                .getCompoundTag("ApeironStockingInput")
                .hasKey("refunds"),
            "clipboard duplicated recovery inventory");
        network.rejectInsert = false;
        restored.tick(100);
        check(
            restored.getRefundAmount(false)
                .signum() == 0
                && restored.getRefundAmount(true)
                    .signum() == 0,
            "refund retry failed");
        check(
            network.count(item())
                .equals(total)
                && network.count(fluid())
                    .equals(total),
            "refund did not restore exact total");
    }

    private static void verifySelectionsAndPersistence() {
        Network network = new Network();
        network.store(item(), BigInteger.TEN.pow(60));
        network.store(fluid(), BigInteger.TEN.pow(30));
        Input input = new Input(network, StockingInputLogic.Kind.MIXED);
        check(input.setMark(359, item()) && !input.setMark(358, item()), "duplicate selection accepted");
        check(input.setMark(0, fluid()), "fluid selection rejected");
        input.getFilter()
            .setMinimum("1e18");
        input.getFilter()
            .setModId("minecraft");
        input.getFilter()
            .setItemId("*");
        NBTTagCompound tag = input.copyConfiguration();
        Input restored = new Input(network, StockingInputLogic.Kind.MIXED);
        check(restored.pasteConfiguration(tag), "configuration paste failed");
        check(
            restored.getMark(359) != null && restored.getMark(0) != null
                && restored.getFilter()
                    .getMinimum()
                    .equals(BigInteger.TEN.pow(18)),
            "last slot or scientific minimum lost");
        Input bus = new Input(network, StockingInputLogic.Kind.ITEMS);
        check(!bus.setMark(0, fluid()), "bus accepted fluid");
        input.getFilter()
            .setItemId("diamond");
        input.setAutoPull(true);
        check(
            input.getDisplayed(0) instanceof IAEItemStack && input.getDisplayed(1) == null,
            "combined automatic filter failed");
        input.getFilter()
            .setMinimum("1e80");
        input.changed();
        check(input.getDisplayed(0) == null, "minimum was narrowed to long");
        input.connected = false;
        input.begin();
        check(input.itemViews().length == 0 && input.fluidViews().length == 0, "offline inputs supplied material");
        input.end();
    }

    private static final class Provider extends MTEInfiniteProgrammingCircuitProvider {

        private Provider() {
            super("apeiron.verification.circuit_provider", 10, new String[0], null);
            ApeironMachineTile tile = new ApeironMachineTile();
            tile.setInitialValuesAsNBT(
                null,
                (short) ApeironConfig.getMachineId(ApeironMachines.CIRCUIT_PROVIDER_OFFSET));
            setBaseMetaTileEntity(tile);
        }

        @Override
        public boolean isActive() {
            return true;
        }
    }

    private static void verifyProvider() {
        Provider provider = new Provider();
        check(
            provider.isItemValidForSlot(80, new ItemStack(Items.diamond)),
            "physical sample slots rejected insertion");
        provider.mInventory[0] = new ItemStack(Items.diamond, 64);
        provider.rebuildCircuits();
        ItemList available = new ItemList();
        provider.getCircuitInventory()
            .getAvailableItems(available, 0);
        IAEItemStack circuit = null;
        for (IAEItemStack candidate : available)
            if (reobf.proghatches.item.ItemProgrammingCircuit.getCircuit(candidate.getItemStack())
                .map(ItemStack::getItem)
                .orElse(null) == Items.diamond) circuit = candidate;
        check(circuit != null && BigAEStackValues.isInfinite(circuit), "programming circuit was not renewable");
        BigInteger huge = BigInteger.TEN.pow(90);
        IAEItemStack request = (IAEItemStack) amount(circuit, huge);
        for (Actionable mode : new Actionable[] { Actionable.SIMULATE, Actionable.MODULATE, Actionable.MODULATE }) {
            IAEItemStack result = provider.getCircuitInventory()
                .extractItems(request, mode, null);
            check(
                result != null && !BigAEStackValues.isInfinite(result)
                    && BigAEStackValues.get(result)
                        .equals(huge),
                "infinite provider truncated extraction");
        }
        check(provider.mInventory[0].stackSize == 64, "provider consumed physical samples");
        check(
            provider.getProxy()
                .getIdlePowerUsage() == PowerUnits.EU.convertTo(PowerUnits.AE, 1024),
            "provider idle cost incorrect");
        provider.mInventory[0] = new ItemStack(Items.emerald);
        provider.rebuildCircuits();
        check(
            provider.getCircuitInventory()
                .extractItems(request, Actionable.SIMULATE, null) == null,
            "removed template remained available");
        for (int id = 0; id < GregTechAPI.METATILEENTITIES.length; id++) {
            IMetaTileEntity meta = GregTechAPI.METATILEENTITIES[id];
            if (meta instanceof reobf.proghatches.gt.metatileentity.ProgrammingCircuitProviderPrefabricated) {
                provider.mInventory[80] = new ItemStack(GregTechAPI.sBlockMachines, 1, id);
                provider.rebuildCircuits();
                available = new ItemList();
                provider.getCircuitInventory()
                    .getAvailableItems(available, 0);
                for (ItemStack original : ((reobf.proghatches.gt.metatileentity.ProgrammingCircuitProviderPrefabricated) meta)
                    .getCircuit()) {
                    IAEItemStack expanded = available.findPrecise(AEItemStack.create(original));
                    check(expanded != null && BigAEStackValues.isInfinite(expanded), "prefab circuit omitted");
                }
                return;
            }
        }
        throw new IllegalStateException("No prefabricated provider registered");
    }

    private static final class Input extends StockingInputLogic {

        private final Network network;
        private boolean connected = true;

        private Input(Network network, Kind kind) {
            super(null, kind);
            this.network = network;
        }

        @Override
        protected boolean active() {
            return connected;
        }

        @Override
        protected BaseActionSource source() {
            return null;
        }

        @Override
        protected IMEInventory network(IAEStack<?> stack) {
            return network;
        }

        @Override
        protected List<IAEStack<?>> readNetworkStocks() {
            List<IAEStack<?>> stocks = new ArrayList<>();
            for (IAEStack<?> stack : network.stored) stocks.add(stack.copy());
            return stocks;
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static final class Network implements IMEInventory, BigMEInventory {

        private final List<IAEStack<?>> stored = new ArrayList<>();
        private boolean partialFluid, rejectInsert;

        private IAEStack<?> find(IAEStack<?> type) {
            for (IAEStack<?> stack : stored) if (StockingInputLogic.same(stack, type)) return stack;
            return null;
        }

        private void store(IAEStack<?> type, BigInteger size) {
            IAEStack<?> stack = find(type);
            if (stack == null) stored.add(amount(type, size));
            else BigAEStackValues.set(stack, size);
        }

        private BigInteger count(IAEStack<?> type) {
            return BigAEStackValues.get(find(type));
        }

        @Override
        public IAEStack injectItems(IAEStack stack, Actionable mode, BaseActionSource source) {
            return (IAEStack) injectItemsBig(stack, mode, source);
        }

        @Override
        public IAEStack extractItems(IAEStack stack, Actionable mode, BaseActionSource source) {
            return (IAEStack) extractItemsBig(stack, mode, source);
        }

        @Override
        public IAEStack<?> injectItemsBig(IAEStack<?> input, Actionable mode, BaseActionSource source) {
            if (rejectInsert) return input;
            if (mode == Actionable.MODULATE) store(input, count(input).add(BigAEStackValues.get(input)));
            return null;
        }

        @Override
        public IAEStack<?> extractItemsBig(IAEStack<?> request, Actionable mode, BaseActionSource source) {
            IAEStack<?> stock = find(request);
            if (stock == null) return null;
            BigInteger moved = count(request).min(BigAEStackValues.get(request));
            if (mode == Actionable.MODULATE && partialFluid
                && request instanceof appeng.api.storage.data.IAEFluidStack) {
                moved = moved.divide(BigInteger.valueOf(2));
                partialFluid = false;
            }
            if (moved.signum() <= 0) return null;
            if (mode == Actionable.MODULATE) store(stock, count(stock).subtract(moved));
            return amount(request, moved);
        }

        @Override
        public IItemList getAvailableItems(IItemList out, int iteration) {
            for (IAEStack<?> stack : stored) out.addStorage(stack.copy());
            return out;
        }

        @Override
        public StorageChannel getChannel() {
            return StorageChannel.ITEMS;
        }
    }
}
