package com.silvia.apeiron.common.machine.me.stocking.verification;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.stack.InfiniteAEStack;
import com.silvia.apeiron.ae.storage.BigMEInventory;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.me.circuit.MTEInfiniteProgrammingCircuitProvider;
import com.silvia.apeiron.common.machine.me.stocking.MTEInfiniteStorageInputAssembly;
import com.silvia.apeiron.common.machine.me.stocking.MTEInfiniteStorageInputBus;
import com.silvia.apeiron.common.machine.me.stocking.MTEInfiniteStorageInputHatch;
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
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.TurbineStatCalculator;
import gregtech.common.items.IDMetaTool01;
import gregtech.common.items.MetaGeneratedTool01;
import gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace;
import gregtech.common.tileentities.machines.multi.turbines.MTELargeTurbineSteam;
import gtneioreplugin.plugin.block.BlockDimensionDisplay;
import gtneioreplugin.plugin.block.ModBlocks;
import tectech.TecTech;
import tectech.recipe.EyeOfHarmonyRecipe;
import tectech.recipe.EyeOfHarmonyRecipeStorage;

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
        verifyNativeExtraction();
        verifyTurbineConsumption();
        verifyBoilerConsumption();
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
            setBaseMetaTileEntity(new BaseMetaTileEntity());
        }

        @Override
        public StockingInputLogic getStockingInput() {
            return input;
        }
    }

    private static final class Bus extends MTEInfiniteStorageInputBus {

        private final Input input;

        private Bus(Input input) {
            super("apeiron.verification.stocking_bus", 6, new String[0], null, StockingInputLogic.Kind.ITEMS);
            this.input = input;
            setBaseMetaTileEntity(new BaseMetaTileEntity());
        }

        @Override
        public StockingInputLogic getStockingInput() {
            return input;
        }
    }

    private static final class Hatch extends MTEInfiniteStorageInputHatch {

        private final Input input;

        private Hatch(Input input) {
            super("apeiron.verification.stocking_hatch", 6, new String[0], null);
            this.input = input;
            setBaseMetaTileEntity(new BaseMetaTileEntity());
        }

        @Override
        public StockingInputLogic getStockingInput() {
            return input;
        }
    }

    private static void verifyNativeExtraction() {
        BigInteger total = BigInteger.TEN.pow(50);
        for (boolean mixed : new boolean[] { false, true }) {
            Network network = new Network();
            network.store(item(), total);
            network.store(fluid(), total);
            Input items = new Input(network, mixed ? StockingInputLogic.Kind.MIXED : StockingInputLogic.Kind.ITEMS);
            Input fluids = mixed ? items : new Input(network, StockingInputLogic.Kind.FLUIDS);
            items.setMark(0, item());
            fluids.setMark(359, fluid());
            MTEInfiniteStorageInputBus bus = mixed ? new Assembly(items) : new Bus(items);
            MTEInfiniteStorageInputHatch hatch = mixed ? ((Assembly) bus).getFluidInput() : new Hatch(fluids);
            MTEMultiBlockBase controller = new MTEElectricBlastFurnace("apeiron.verification.native_stocking");
            controller.setBaseMetaTileEntity(new BaseMetaTileEntity());
            for (int scan = 0; scan < 2; scan++) {
                controller.clearHatches();
                if (mixed) {
                    for (int repeat = 0; repeat < 3; repeat++) {
                        check(
                            controller.addToMachineList(bus.getBaseMetaTileEntity(), 0),
                            "generic assembly registration");
                        check(
                            controller.addInputBusToMachineList(bus.getBaseMetaTileEntity(), 0),
                            "assembly bus registration");
                        check(
                            controller.addInputHatchToMachineList(bus.getBaseMetaTileEntity(), 0),
                            "assembly fluid registration");
                    }
                } else {
                    check(
                        controller.addInputBusToMachineList(bus.getBaseMetaTileEntity(), 0),
                        "standalone bus registration");
                    check(
                        controller.addInputHatchToMachineList(hatch.getBaseMetaTileEntity(), 0),
                        "standalone fluid registration");
                }
                check(
                    HatchElement.InputBus.count(controller) == 1 && HatchElement.InputHatch.count(controller) == 1,
                    "mixed source counted twice or omitted");
                check(controller.mDualInputHatches.isEmpty(), "storage input was isolated as a pattern inventory");
            }
            check(
                controller.getStoredInputs()
                    .size() == 1
                    && controller.getStoredFluids()
                        .size() == 1,
                "native getter omitted marked material");
            if (mixed) {
                check(
                    hatch.getStockingInput() == items && hatch.getProxy() == bus.getProxy(),
                    "fluid view has a separate session or node");
                check(
                    bus.getBaseMetaTileEntity()
                        .getMetaTileEntity() == bus,
                    "fluid view replaced placed assembly");
            }
            FluidStack water = new FluidStack(FluidRegistry.WATER, 13);
            check(hatch.drain(ForgeDirection.UNKNOWN, water, false).amount == 13, "direct fluid simulation");
            check(
                network.count(fluid())
                    .equals(total),
                "simulation consumed fluid");
            check(hatch.drain(ForgeDirection.NORTH, water, true) == null, "physical pipe extracted a virtual fluid");
            check(hatch.drain(ForgeDirection.UNKNOWN, water, 17, true).amount == 17, "explicit-amount direct drain");
            check(bus.decrStackSize(0, 3).stackSize == 3, "direct item extraction");
            check(
                network.count(fluid())
                    .equals(total.subtract(BigInteger.valueOf(17)))
                    && network.count(item())
                        .equals(total.subtract(BigInteger.valueOf(3))),
                "direct extraction did not charge ME");
            check(
                hatch.getTankInfo(ForgeDirection.UNKNOWN).length == 1
                    && hatch.getTankInfo(ForgeDirection.NORTH).length == 0,
                "controller tank projection");
            controller.startRecipeProcessing();
            check(controller.depleteInput(water, true), "transactional fluid simulation");
            check(controller.depleteInput(water), "transactional fluid drain");
            check(controller.depleteInput(new ItemStack(Items.diamond, 7)), "transactional item decrement");
            check(
                network.count(fluid())
                    .equals(total.subtract(BigInteger.valueOf(17))),
                "transaction drained before commit");
            controller.endRecipeProcessing();
            controller.endRecipeProcessing();
            check(
                network.count(fluid())
                    .equals(total.subtract(BigInteger.valueOf(30)))
                    && network.count(item())
                        .equals(total.subtract(BigInteger.TEN)),
                "group completion omitted or repeated native debits");
            fluids.connected = false;
            check(hatch.drain(ForgeDirection.UNKNOWN, water, true) == null, "disconnected fluid still available");
            check(hatch.getStoredFluids().length == 0, "disconnected hatch advertised cached fluid");
            items.connected = false;
            check(bus.getStackInSlot(0) == null, "disconnected bus advertised cached fuel");
            check(bus.decrStackSize(-1, 1) == null && bus.decrStackSize(0, 0) == null, "invalid item extraction");
        }
        Apeiron.LOG.info(
            "Stocking native controller API verification passed: separate/mixed inputs, structure counts, simulation, direct extraction and grouped debits");
    }

    private static void verifyTurbineConsumption() {
        ItemStack rotor = MetaGeneratedTool01.INSTANCE
            .getToolWithStats(IDMetaTool01.TURBINE.ID, 1, Materials.Steel, Materials.Steel, null);
        TurbineStatCalculator stats = new TurbineStatCalculator(MetaGeneratedTool01.INSTANCE, rotor);
        int flow = (int) (stats.getOptimalSteamFlow() * (0.5f * stats.getOverflowEfficiency() + 1));
        check(flow > 0, "turbine fixture has no flow");
        IAEStack<?> steam = AEFluidStack.create(Materials.Steam.getGas(1));
        BigInteger total = BigInteger.TEN.pow(40);
        for (boolean mixed : new boolean[] { false, true }) {
            Network network = new Network();
            network.store(steam, total);
            Input input = new Input(network, mixed ? StockingInputLogic.Kind.MIXED : StockingInputLogic.Kind.FLUIDS);
            input.setMark(359, steam);
            MTEInfiniteStorageInputHatch hatch;
            MTEInfiniteStorageInputAssembly assembly = mixed ? new Assembly(input) : null;
            hatch = mixed ? assembly.getFluidInput() : new Hatch(input);
            MTELargeTurbineSteam turbine = new MTELargeTurbineSteam("apeiron.verification.stocking_turbine") {

                @Override
                public long getMaximumOutput() {
                    return Integer.MAX_VALUE;
                }
            };
            turbine.setBaseMetaTileEntity(new BaseMetaTileEntity());
            try {
                java.lang.reflect.Field achievement = MTELargeTurbineSteam.class.getDeclaredField("achievement");
                achievement.setAccessible(true);
                achievement.setBoolean(turbine, true);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Turbine fixture setup failed", error);
            }
            check(
                turbine.addInputHatchToMachineList(
                    mixed ? assembly.getBaseMetaTileEntity() : hatch.getBaseMetaTileEntity(),
                    0),
                "turbine input registration");
            for (int tick = 1; tick <= 4; tick++) {
                turbine.startRecipeProcessing();
                check(turbine.fluidIntoPower(turbine.getStoredFluids(), stats) > 0, "real turbine rejected steam");
                turbine.endRecipeProcessing();
                check(
                    network.count(steam)
                        .equals(total.subtract(BigInteger.valueOf((long) flow * tick))),
                    "real turbine generated power without consuming exact steam");
            }
            check(turbine.fluidIntoPower(turbine.getStoredFluids(), stats) > 0, "direct turbine flow failed");
            check(
                network.count(steam)
                    .equals(total.subtract(BigInteger.valueOf((long) flow * 5))),
                "unwrapped turbine tick did not debit steam");
            network.store(steam, BigInteger.ZERO);
            input.refresh();
            check(turbine.fluidIntoPower(turbine.getStoredFluids(), stats) == 0, "empty steam network generated power");
        }
        Apeiron.LOG.info(
            "Steam turbine stocking verification passed: real flow calculation, repeated exact steam consumption and empty network");
    }

    public static void verifyEyeDrain() throws ReflectiveOperationException {
        IAEStack<?> hydrogen = AEFluidStack.create(Materials.Hydrogen.getGas(1));
        IAEStack<?> helium = AEFluidStack.create(Materials.Helium.getGas(1));
        IAEStack<?> arrays = AEItemStack.create(tectech.thing.CustomItemList.astralArrayFabricator.get(1));
        EyeOfHarmonyRecipeStorage originalStorage = TecTech.eyeOfHarmonyRecipeStorage;
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field singleton = unsafeClass.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        FixtureStorage fixtureStorage = (FixtureStorage) unsafeClass.getMethod("allocateInstance", Class.class)
            .invoke(singleton.get(null), FixtureStorage.class);
        fixtureStorage.recipe = new EyeOfHarmonyRecipe(
            new ArrayList<>(),
            new BlockDimensionDisplay("Ow"),
            1.5D,
            10_000_000_000L,
            20_000_000_000L,
            20,
            0,
            0.8D);
        TecTech.eyeOfHarmonyRecipeStorage = fixtureStorage;
        try {
            for (boolean mixed : new boolean[] { false, true }) {
                Network network = new Network();
                BigInteger total = BigInteger.TEN.pow(30);
                network.store(hydrogen, total);
                network.store(helium, total);
                network.store(arrays, BigInteger.valueOf(100));
                Input logic = new Input(
                    network,
                    mixed ? StockingInputLogic.Kind.MIXED : StockingInputLogic.Kind.FLUIDS);
                logic.setMark(0, hydrogen);
                logic.setMark(1, helium);
                tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony eye = new tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony(
                    "apeiron.verify.eye_me");
                eye.setBaseMetaTileEntity(new BaseMetaTileEntity());
                eye.mInventory[eye.getControllerSlotIndex()] = new ItemStack(ModBlocks.getBlock("Ow"));
                ApeironMachineTile enhancement = new ApeironMachineTile();
                enhancement.setInitialValuesAsNBT(
                    null,
                    (short) ApeironConfig.getMachineId(ApeironMachines.EYE_OF_HARMONY_ENHANCEMENT_OFFSET));
                eye.mInputHatches
                    .add((gregtech.api.metatileentity.implementations.MTEHatchInput) enhancement.getMetaTileEntity());
                if (mixed) {
                    Assembly assembly = new Assembly(logic);
                    check(
                        eye.addInputHatchToMachineList(assembly.getBaseMetaTileEntity(), 0),
                        "eye assembly registration failed");
                } else {
                    eye.mInputHatches.add(new Hatch(logic));
                }
                java.lang.reflect.Field tanks = tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony.class
                    .getDeclaredField("validFluidMap");
                tanks.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.Map<net.minecraftforge.fluids.Fluid, Long> stored = (java.util.Map<net.minecraftforge.fluids.Fluid, Long>) tanks
                    .get(eye);
                java.lang.reflect.Method drain = tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony.class
                    .getDeclaredMethod("drainFluidFromHatchesAndStoreInternally");
                drain.setAccessible(true);
                logic.addWatcher(eye);
                eye.mMachine = true;
                eye.onPreTick(eye.getBaseMetaTileEntity(), 2L);
                check(
                    network.count(hydrogen)
                        .equals(total.subtract(BigInteger.TEN.pow(10))),
                    "eye hydrogen debit was truncated or repeated");
                check(
                    network.count(helium)
                        .equals(total.subtract(BigInteger.valueOf(20_000_000_000L))),
                    "eye helium debit was truncated or repeated");
                check(
                    stored.get(Materials.Hydrogen.mGas) == 10_000_000_000L
                        && stored.get(Materials.Helium.mGas) == 20_000_000_000L,
                    "eye did not charge both fluids to their exact recipe requirements");
                drain.invoke(eye);
                check(
                    network.count(hydrogen)
                        .equals(total.subtract(BigInteger.TEN.pow(10)))
                        && network.count(helium)
                            .equals(total.subtract(BigInteger.valueOf(20_000_000_000L))),
                    "eye continued pulling fluid after the recipe requirements were met");
                check(!logic.isProcessing(), "eye drain left an open input session");
                check(
                    network.count(arrays)
                        .equals(BigInteger.valueOf(100)),
                    "eye fluid drain consumed unrelated items");

                // A completion can refill the next recipe while its native ME processing bracket is open.
                stored.put(Materials.Hydrogen.mGas, 0L);
                stored.put(Materials.Helium.mGas, 0L);
                network.store(hydrogen, total);
                network.store(helium, total);
                logic.begin();
                drain.invoke(eye);
                check(logic.isProcessing(), "in-bracket eye refill closed its caller's input session");
                check(
                    logic.end()
                        .wasSuccessful(),
                    "in-bracket eye refill failed its final debit");
                check(
                    network.count(hydrogen)
                        .equals(total.subtract(BigInteger.TEN.pow(10)))
                        && network.count(helium)
                            .equals(total.subtract(BigInteger.valueOf(20_000_000_000L))),
                    "in-bracket eye refill was charged again when the session ended");

                // Start with just hydrogen selected, then add helium. The hydrogen must stop at its own target.
                stored.put(Materials.Hydrogen.mGas, 0L);
                stored.put(Materials.Helium.mGas, 0L);
                network.store(hydrogen, total);
                network.store(helium, total);
                logic.setMark(1, null);
                eye.onPreTick(eye.getBaseMetaTileEntity(), 3L);
                check(
                    stored.get(Materials.Hydrogen.mGas) == 10_000_000_000L && stored.get(Materials.Helium.mGas) == 0L,
                    "first selected fluid was not pulled in one tick");
                logic.setMark(1, helium);
                eye.onPreTick(eye.getBaseMetaTileEntity(), 4L);
                check(
                    stored.get(Materials.Hydrogen.mGas) == 10_000_000_000L
                        && stored.get(Materials.Helium.mGas) == 20_000_000_000L,
                    "late fluid mark overfilled the satisfied fluid");
                check(
                    network.count(hydrogen)
                        .equals(total.subtract(BigInteger.valueOf(10_000_000_000L))),
                    "hydrogen was charged again while waiting for helium");

                // An extraction race may partially withdraw fluid; the common transaction must refund before credit.
                stored.put(Materials.Hydrogen.mGas, 0L);
                stored.put(Materials.Helium.mGas, 0L);
                network.store(hydrogen, total);
                network.store(helium, total);
                logic.refresh();
                network.partialFluid = true;
                drain.invoke(eye);
                check(
                    stored.get(Materials.Hydrogen.mGas) == 0L && stored.get(Materials.Helium.mGas) == 0L
                        && network.count(hydrogen)
                            .equals(total)
                        && network.count(helium)
                            .equals(total),
                    "failed eye fluid transaction credited a tank or lost a refund");
                drain.invoke(eye);
                check(
                    stored.get(Materials.Hydrogen.mGas) == 10_000_000_000L
                        && stored.get(Materials.Helium.mGas) == 20_000_000_000L,
                    "eye did not retry the rolled-back exact fluid request");

                // Existing excess is retained, but it must never cause another network debit for that fluid.
                stored.put(Materials.Hydrogen.mGas, 15_000_000_000L);
                stored.put(Materials.Helium.mGas, 5_000_000_000L);
                network.store(hydrogen, total);
                network.store(helium, total);
                logic.refresh();
                drain.invoke(eye);
                check(
                    stored.get(Materials.Hydrogen.mGas) == 15_000_000_000L
                        && stored.get(Materials.Helium.mGas) == 20_000_000_000L
                        && network.count(hydrogen)
                            .equals(total)
                        && network.count(helium)
                            .equals(total.subtract(BigInteger.valueOf(15_000_000_000L))),
                    "already-full fluid kept draining or pre-existing fluid was destroyed");

                // Predict the next recipe's arrays rather than trusting the previous recipe's parallel field.
                IAEStack<?> plasma = AEFluidStack.create(Materials.RawStarMatter.getFluid(1));
                network.store(plasma, total);
                logic.setMark(2, plasma);
                java.lang.reflect.Field arrayCount = tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony.class
                    .getDeclaredField("astralArrayAmount");
                arrayCount.setAccessible(true);
                arrayCount.setLong(eye, 8637L);
                BigInteger requiredPlasma = com.silvia.apeiron.common.machine.tectech.EyeOfHarmonyFluidRequirements
                    .forRecipe(
                        fixtureStorage.recipe,
                        com.silvia.apeiron.common.machine.tectech.EyeOfHarmonyFluidRequirements
                            .plannedParallels(eye, 8637L, true))
                    .get(Materials.RawStarMatter.mFluid);
                drain.invoke(eye);
                check(
                    BigInteger.valueOf(stored.get(Materials.RawStarMatter.mFluid))
                        .equals(requiredPlasma)
                        && network.count(plasma)
                            .equals(total.subtract(requiredPlasma)),
                    "stellar plasma request used stale parallels or an int-sized debit");

                arrayCount.setLong(eye, 0L);
                stored.put(Materials.RawStarMatter.mFluid, 0L);
                network.store(plasma, total);
                if (mixed) logic.setMark(3, arrays);
                else {
                    gregtech.api.metatileentity.implementations.MTEHatchInputBus bus = new gregtech.api.metatileentity.implementations.MTEHatchInputBus(
                        "apeiron.verify.eye_pending_arrays",
                        1,
                        new String[0],
                        null);
                    bus.setBaseMetaTileEntity(new BaseMetaTileEntity());
                    bus.mInventory[0] = tectech.thing.CustomItemList.astralArrayFabricator.get(1);
                    bus.mInventory[0].stackSize = 100;
                    eye.mInputBusses.add(bus);
                    logic.refresh();
                }
                check(
                    com.silvia.apeiron.common.machine.tectech.EyeOfHarmonyFluidRequirements
                        .plannedParallels(eye, 0L, true) == 4096L,
                    "unabsorbed input arrays were omitted from the next fluid requirement");
                requiredPlasma = com.silvia.apeiron.common.machine.tectech.EyeOfHarmonyFluidRequirements
                    .forRecipe(fixtureStorage.recipe, 4096L)
                    .get(Materials.RawStarMatter.mFluid);
                drain.invoke(eye);
                check(
                    BigInteger.valueOf(stored.get(Materials.RawStarMatter.mFluid))
                        .equals(requiredPlasma)
                        && network.count(arrays)
                            .equals(BigInteger.valueOf(100)),
                    "pending arrays selected the wrong fluid or were consumed by a fluid preview");
                eye.mInventory[eye.getControllerSlotIndex()] = null;
                stored.put(Materials.RawStarMatter.mFluid, 0L);
                drain.invoke(eye);
                check(
                    stored.get(Materials.RawStarMatter.mFluid) == 0L && network.count(plasma)
                        .equals(total.subtract(requiredPlasma)),
                    "eye without a planet pulled recipe fluid");

                // Renewable fluid sources have no finite count, but still stop at the per-recipe target.
                eye.mInventory[eye.getControllerSlotIndex()] = new ItemStack(ModBlocks.getBlock("Ow"));
                if (mixed) logic.setMark(3, null);
                else eye.mInputBusses.clear();
                stored.put(Materials.Hydrogen.mGas, 0L);
                stored.put(Materials.Helium.mGas, 0L);
                network.store(hydrogen, BigInteger.ZERO);
                network.store(helium, BigInteger.ZERO);
                ((InfiniteAEStack) network.find(hydrogen)).setInfinite(true);
                ((InfiniteAEStack) network.find(helium)).setInfinite(true);
                logic.refresh();
                drain.invoke(eye);
                check(
                    stored.get(Materials.Hydrogen.mGas) == 10_000_000_000L
                        && stored.get(Materials.Helium.mGas) == 20_000_000_000L
                        && BigAEStackValues.isInfinite(network.find(hydrogen))
                        && BigAEStackValues.isInfinite(network.find(helium)),
                    "renewable fluids were truncated, depleted or overfilled");

                // Multiple finite sources share one deficit; preserve unrelated items in the common inventory.
                MTEMultiBlockBase physical = new MTEElectricBlastFurnace("apeiron.verify.internal_fluid_targets");
                FluidStack first = Materials.Hydrogen.getGas(40), second = Materials.Hydrogen.getGas(50);
                FluidStack other = new FluidStack(FluidRegistry.WATER, 12);
                ItemStack catalyst = new ItemStack(Items.diamond, 3);
                java.util.Map<net.minecraftforge.fluids.Fluid, Long> finiteTanks = new java.util.HashMap<>();
                finiteTanks.put(Materials.Hydrogen.mGas, 25L);
                com.silvia.apeiron.common.machine.parallel.BigRecipeInventory finite = new com.silvia.apeiron.common.machine.parallel.BigRecipeInventory(
                    physical,
                    new ItemStack[] { catalyst },
                    new FluidStack[] { first, second, other });
                check(
                    finite.fillFluids(
                        finiteTanks,
                        Collections.singletonMap(Materials.Hydrogen.mGas, BigInteger.valueOf(100)))
                        && finiteTanks.get(Materials.Hydrogen.mGas) == 100L
                        && first.amount == 0
                        && second.amount == 15
                        && other.amount == 12
                        && catalyst.stackSize == 3,
                    "common tank refill exceeded the shared deficit or consumed another input");
            }
        } finally {
            TecTech.eyeOfHarmonyRecipeStorage = originalStorage;
        }
        Apeiron.LOG.info(
            "Eye ME input verification passed: one-tick exact hydrogen/helium/plasma, late marks, independent caps, rollback and separate/mixed hatches");
    }

    private static final class FixtureStorage extends EyeOfHarmonyRecipeStorage {

        private EyeOfHarmonyRecipe recipe;

        @Override
        public EyeOfHarmonyRecipe recipeLookUp(ItemStack stack) {
            return recipe;
        }
    }

    private static void verifyBoilerConsumption() {
        Class<?> boilerClass;
        try {
            boilerClass = Class.forName(
                "com.science.gtnl.common.machine.multiblock.structuralReconstructionPlan.LargeBoiler$LargeBoilerTungstenSteel");
        } catch (ClassNotFoundException absent) {
            Apeiron.LOG.info("GTNL not installed; optional tungstensteel boiler stocking verification skipped");
            return;
        }
        IAEStack<?> coal = AEItemStack.create(new ItemStack(Items.coal));
        BigInteger total = BigInteger.TEN.pow(40);
        for (boolean mixed : new boolean[] { false, true }) {
            try {
                Network network = new Network();
                network.store(coal, BigInteger.valueOf(64));
                network.store(fluid(), total);
                Input items = new Input(network, mixed ? StockingInputLogic.Kind.MIXED : StockingInputLogic.Kind.ITEMS);
                Input fluids = mixed ? items : new Input(network, StockingInputLogic.Kind.FLUIDS);
                items.setMark(0, coal);
                fluids.setMark(359, fluid());
                MTEInfiniteStorageInputBus bus = mixed ? new Assembly(items) : new Bus(items);
                MTEInfiniteStorageInputHatch hatch = mixed ? ((Assembly) bus).getFluidInput() : new Hatch(fluids);
                MTEMultiBlockBase boiler = (MTEMultiBlockBase) boilerClass.getConstructor(String.class)
                    .newInstance("apeiron.verification.stocking_boiler");
                boiler.setBaseMetaTileEntity(new BaseMetaTileEntity());
                check(boiler.addInputBusToMachineList(bus.getBaseMetaTileEntity(), 0), "boiler fuel registration");
                check(
                    boiler.addInputHatchToMachineList(
                        mixed ? bus.getBaseMetaTileEntity() : hatch.getBaseMetaTileEntity(),
                        0),
                    "boiler water registration");
                boiler.startRecipeProcessing();
                check(
                    boiler.checkProcessing()
                        .wasSuccessful(),
                    "real GTNL boiler rejected coal");
                boiler.endRecipeProcessing();
                check(
                    network.count(coal)
                        .equals(BigInteger.valueOf(63)),
                    "boiler fuel not charged exactly once");
                for (int tick = 0; tick < 4; tick++) check(boiler.onRunningTick(null), "boiler water tick failed");
                long generated = (long) boiler.mEUt * 2 * 4;
                long expected = (generated + gregtech.api.enums.GTValues.STEAM_PER_WATER - 1)
                    / gregtech.api.enums.GTValues.STEAM_PER_WATER;
                check(
                    network.count(fluid())
                        .equals(total.subtract(BigInteger.valueOf(expected))),
                    "boiler running water not consumed exactly");
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("GTNL boiler stocking verification failed", error);
            }
        }
        Apeiron.LOG.info(
            "GTNL tungstensteel boiler stocking verification passed: separate/mixed water and coal, fuel check and repeated running ticks");
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
            boolean renewable = BigAEStackValues.isInfinite(stock);
            BigInteger moved = renewable ? BigAEStackValues.get(request)
                : count(request).min(BigAEStackValues.get(request));
            if (mode == Actionable.MODULATE && partialFluid
                && request instanceof appeng.api.storage.data.IAEFluidStack) {
                moved = moved.divide(BigInteger.valueOf(2));
                partialFluid = false;
            }
            if (moved.signum() <= 0) return null;
            if (mode == Actionable.MODULATE && !renewable) store(stock, count(stock).subtract(moved));
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
