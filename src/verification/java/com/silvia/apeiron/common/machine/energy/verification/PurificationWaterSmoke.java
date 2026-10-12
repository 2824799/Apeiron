package com.silvia.apeiron.common.machine.energy.verification;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.api.machine.parallel.BigPurificationUnit;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.energy.MachineRecipeDisplay;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.me.output.storage.BigMEOutputProvider;
import com.silvia.apeiron.common.machine.me.stocking.verification.StockingInputsSmoke.Fixture;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;

import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.MEMonitorHandler;
import appeng.api.storage.data.IAEStack;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.storage.MEInventoryHandler;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEFluidStackType;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.metadata.PurificationPlantBaseChanceKey;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.tileentities.machines.multi.purification.MTEHatchDegasifierControl;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationPlant;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitBaryonicPerfection;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitBase;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitClarifier;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitDegasser;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitFlocculation;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitOzonation;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitPhAdjustment;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitPlasmaHeater;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitUVTreatment;

/** Exercises all eight native cycle implementations, with real ME transactions and >int water quantities. */
public final class PurificationWaterSmoke {

    private static final BigInteger STOCK = BigInteger.TEN.pow(40);
    private static final int PARALLEL = 17070;
    private static final RecipeMap<?> MAP = RecipeMapBuilder.of("apeiron.verify.purification")
        .maxIO(1, 1, 2, 1)
        .build();
    private static final RecipeMap<?> FILTER_MAP = RecipeMapBuilder.of("apeiron.verify.purification.filter")
        .maxIO(1, 1, 2, 1)
        .build();
    private static final GTRecipe RECIPE = GTRecipeBuilder.builder()
        .itemOutputs(new ItemStack(Items.emerald))
        .fluidInputs(new FluidStack(FluidRegistry.WATER, 2_000_000), new FluidStack(FluidRegistry.LAVA, 3))
        .fluidOutputs(new FluidStack(FluidRegistry.WATER, 1_500_000))
        .duration(20)
        .eut(8)
        .metadata(PurificationPlantBaseChanceKey.INSTANCE, 100.0f)
        .build()
        .get();
    private static final GTRecipe FILTER_RECIPE = GTRecipeBuilder.builder()
        .itemInputs(new ItemStack(Items.diamond, 0))
        .itemOutputs(new ItemStack(Items.emerald))
        .fluidInputs(new FluidStack(FluidRegistry.WATER, 2_000_000), new FluidStack(FluidRegistry.LAVA, 3))
        .fluidOutputs(new FluidStack(FluidRegistry.WATER, 1_500_000))
        .duration(20)
        .eut(8)
        .metadata(PurificationPlantBaseChanceKey.INSTANCE, 100.0f)
        .build()
        .get();

    private PurificationWaterSmoke() {}

    private static class Clarifier extends MTEPurificationUnitClarifier {

        Clarifier() {
            super("apeiron.verify.water.clarifier");
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return FILTER_MAP;
        }
    }

    private static class Ozone extends MTEPurificationUnitOzonation {

        Ozone() {
            super("apeiron.verify.water.ozone");
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return MAP;
        }
    }

    private static class Flocculation extends MTEPurificationUnitFlocculation {

        Flocculation() {
            super("apeiron.verify.water.flocculation");
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return MAP;
        }
    }

    private static class Ph extends MTEPurificationUnitPhAdjustment {

        Ph() {
            super("apeiron.verify.water.ph");
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return MAP;
        }
    }

    private static class Plasma extends MTEPurificationUnitPlasmaHeater {

        Plasma() {
            super("apeiron.verify.water.plasma");
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return MAP;
        }
    }

    private static class UV extends MTEPurificationUnitUVTreatment {

        UV() {
            super("apeiron.verify.water.uv");
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return MAP;
        }
    }

    private static class Degasser extends MTEPurificationUnitDegasser {

        Degasser() {
            super("apeiron.verify.water.degasser");
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return MAP;
        }
    }

    private static class Baryonic extends MTEPurificationUnitBaryonicPerfection {

        Baryonic() {
            super("apeiron.verify.water.baryonic");
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return MAP;
        }
    }

    static MTEPurificationUnitBase<?> unit(int kind) {
        switch (kind) {
            case 0:
                return new Clarifier();
            case 1:
                return new Ozone();
            case 2:
                return new Flocculation();
            case 3:
                return new Ph();
            case 4:
                return new Plasma();
            case 5:
                return new UV();
            case 6:
                return new Degasser();
            default:
                return new Baryonic();
        }
    }

    private static void prepare(MTEPurificationUnitBase<?> unit) throws ReflectiveOperationException {
        unit.setBaseMetaTileEntity(new BaseMetaTileEntity());
        MTEPurificationPlant plant = new MTEPurificationPlant("apeiron.verify.water.plant");
        plant.setBaseMetaTileEntity(new BaseMetaTileEntity());
        field(MTEPurificationUnitBase.class, "controller").set(unit, plant);
        unit.setMaxParallel(PARALLEL);
        field(MTEPurificationUnitBase.class, "currentRecipe")
            .set(unit, unit instanceof Clarifier ? FILTER_RECIPE : RECIPE);
        field(MTEPurificationUnitBase.class, "effectiveParallel").setInt(unit, PARALLEL);
        unit.mWrench = unit.mScrewdriver = unit.mSoftMallet = unit.mHardHammer = unit.mSolderingTool = unit.mCrowbar = true;
        if (unit instanceof Degasser) {
            MTEHatchDegasifierControl control = new MTEHatchDegasifierControl(
                "apeiron.verify.degas.control",
                8,
                new String[0],
                null);
            control.setBaseMetaTileEntity(new BaseMetaTileEntity());
            field(MTEPurificationUnitDegasser.class, "controlHatch").set(unit, control);
        }
    }

    public static void verify() {
        MAP.addRecipe(RECIPE);
        FILTER_MAP.addRecipe(FILTER_RECIPE);
        boolean fixtureLens = MTEPurificationUnitUVTreatment.LENS_ITEMS.isEmpty();
        if (fixtureLens) MTEPurificationUnitUVTreatment.LENS_ITEMS.add(new ItemStack(Items.diamond));
        try {
            for (int kind = 0; kind < 8; kind++)
                for (boolean mixed : new boolean[] { false, true }) verifyCycle(kind, mixed);
            verifyRollback();
            verifyLimitedWater();
            verifyNativeWater();
            verifyWirelessOutputs();
            verifyFailedOutputs(false);
            verifyFailedOutputs(true);
            verifyRepeatedOutputs();
            verifyUltimatePlant();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Purification water verification failed", error);
        } finally {
            if (fixtureLens) MTEPurificationUnitUVTreatment.LENS_ITEMS.clear();
        }
        Apeiron.LOG.info(
            "Purification water verification passed: all eight units, fluid/mixed stocking, >int parallel water/debit/output/boost, fixed auxiliary cost, reload and rollback");
    }

    private static void verifyCycle(int kind, boolean mixed) throws ReflectiveOperationException {
        MTEPurificationUnitBase<?> unit = unit(kind);
        prepare(unit);
        Fixture input = new Fixture(mixed);
        IAEStack<?> water = AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1));
        IAEStack<?> lava = AEFluidStack.create(new FluidStack(FluidRegistry.LAVA, 1));
        input.store(0, water, STOCK);
        input.store(1, lava, BigInteger.valueOf(10));
        if (mixed) input.store(2, AEItemStack.create(new ItemStack(Items.diamond)), BigInteger.valueOf(64));
        input.attach(unit, mixed);
        // Clarifier's filter is an ordinary non-consumed catalyst for the fluid-only fixture.
        if (!mixed && kind == 0) {
            gregtech.api.metatileentity.implementations.MTEHatchInputBus bus = new gregtech.api.metatileentity.implementations.MTEHatchInputBus(
                "apeiron.verify.filter",
                1,
                new String[0],
                null);
            bus.setBaseMetaTileEntity(new BaseMetaTileEntity());
            bus.mInventory[0] = new ItemStack(Items.diamond, 64);
            unit.mInputBusses.add(bus);
        }
        // Use the actual shared recipe check; the UV and ozone overrides still execute.
        input.logic()
            .begin();
        CheckRecipeResult found;
        try {
            found = unit.checkProcessing();
        } finally {
            input.logic()
                .end();
        }
        check(found.wasSuccessful(), kind + " recipe check failed: " + found.getID());
        check(
            field(MTEPurificationUnitBase.class, "effectiveParallel").getInt(unit) == PARALLEL,
            kind + " water availability was truncated to int");
        check(
            input.count(water)
                .equals(STOCK),
            "recipe check consumed water");
        unit.startCycle(20, 0);
        verifyPreview(unit);
        BigInteger used = BigInteger.valueOf(2_000_000L * PARALLEL + 150_000L * PARALLEL);
        check(
            input.count(water)
                .equals(STOCK.subtract(used)),
            kind + " water/boost debit was truncated or duplicated");
        check(
            input.count(lava)
                .equals(BigInteger.valueOf(7)),
            kind + " auxiliary input incorrectly scaled");
        check(unit.getActualPowerUsage() == unit.getBasePowerUsage() * PARALLEL, "plant power parallel changed");
        check(
            !input.logic()
                .isProcessing(),
            "cycle retained input transaction");
        NBTTagCompound saved = new NBTTagCompound();
        unit.saveNBTData(saved);
        MTEPurificationUnitBase<?> restored = unit(kind);
        prepare(restored);
        restored.loadNBTData(saved);
        field(MTEPurificationUnitBase.class, "currentRecipe").set(restored, null);
        verifyPreview(restored);
        successfulStage(restored, kind);
        MTEInfiniteMEOutputAssembly output = output(restored);
        restored.endCycle();
        BigInteger expected = BigInteger.valueOf(1_500_000L * PARALLEL * (kind == 6 ? 2 : 1));
        check(
            output.getFluidProvider()
                .getCachedAmountBig()
                .equals(expected),
            kind + " cycle output was truncated");
        check(
            output.getProvider()
                .getCachedAmountBig()
                .equals(BigInteger.ONE),
            "item output incorrectly scaled");
        verifyNetworkOutput(output, expected);
        restored.endCycle();
        check(
            output.getFluidProvider()
                .getCachedAmountBig()
                .signum() == 0,
            "idle endCycle duplicated water");
        check(
            ((com.silvia.apeiron.api.machine.parallel.BigPurificationUnit) restored).getPurificationWaterOutputBig()
                .signum() == 0,
            "cycle retained old exact output");
    }

    private static void verifyPreview(MTEPurificationUnitBase<?> unit) {
        BigMachineOutputQueue preview = new BigMachineOutputQueue();
        preview.load(MachineRecipeDisplay.snapshot(unit));
        check(
            preview.getFluidAmountBig()
                .equals(BigInteger.valueOf(1_500_000L * PARALLEL)),
            "purification GUI displayed truncated water");
    }

    private static void successfulStage(MTEPurificationUnitBase<?> unit, int kind) throws ReflectiveOperationException {
        if (kind == 3) field(MTEPurificationUnitPhAdjustment.class, "currentpHValue").setFloat(unit, 7.0f);
        if (kind == 6) {
            Object signal = field(MTEPurificationUnitDegasser.class, "controlSignal").get(unit);
            field(signal.getClass(), "signal").setByte(signal, (byte) 8);
            field(MTEPurificationUnitDegasser.class, "outputMultiplier").setFloat(unit, 2.0f);
        }
        if (kind == 7) field(MTEPurificationUnitBaryonicPerfection.class, "correctStartIndex").setInt(unit, 0);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void verifyNetworkOutput(MTEInfiniteMEOutputAssembly output, BigInteger expected)
        throws ReflectiveOperationException {
        Fixture network = new Fixture(false);
        boolean[] connected = { false };
        double[] power = { 0.0 };
        MEMonitorHandler fluids = new MEMonitorHandler(
            new MEInventoryHandler((IMEInventory) network.inventory(), AEFluidStackType.FLUID_STACK_TYPE));
        MEMonitorHandler items = new MEMonitorHandler(
            new MEInventoryHandler((IMEInventory) network.inventory(), AEItemStackType.ITEM_STACK_TYPE));
        IStorageGrid storage = (IStorageGrid) Proxy.newProxyInstance(
            IStorageGrid.class.getClassLoader(),
            new Class<?>[] { IStorageGrid.class },
            (proxy, method, args) -> {
                if (method.getName()
                    .equals("getFluidInventory")) return fluids;
                if (method.getName()
                    .equals("getItemInventory")) return items;
                throw new UnsupportedOperationException(method.getName());
            });
        IEnergyGrid energy = (IEnergyGrid) Proxy.newProxyInstance(
            IEnergyGrid.class.getClassLoader(),
            new Class<?>[] { IEnergyGrid.class },
            (proxy, method, args) -> {
                if (method.getName()
                    .equals("extractAEPower")) {
                    double granted = Math.min((Double) args[0], power[0]);
                    if (args[1] == appeng.api.config.Actionable.MODULATE) power[0] -= granted;
                    return granted;
                }
                throw new UnsupportedOperationException(method.getName());
            });
        AENetworkProxy proxy = new AENetworkProxy(output, "apeiron.verify.purification.output", null, false) {

            @Override
            public boolean isActive() {
                return connected[0];
            }

            @Override
            public IEnergyGrid getEnergy() {
                return energy;
            }

            @Override
            public IStorageGrid getStorage() {
                return storage;
            }
        };
        field(BigMEOutputProvider.class, "proxy").set(output.getProvider(), proxy);
        output.getFluidProvider()
            .flushCachedStackBig();
        check(
            output.getFluidProvider()
                .getCachedAmountBig()
                .equals(expected),
            "disconnected ME lost water");
        connected[0] = true;
        power[0] = 1000.0;
        output.getFluidProvider()
            .flushCachedStackBig();
        BigInteger moved = network.count(AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)));
        check(moved.signum() > 0 && moved.compareTo(expected) < 0, "finite ME power did not limit transfer");
        check(
            output.getFluidProvider()
                .getCachedAmountBig()
                .equals(expected.subtract(moved)),
            "partial ME transfer lost its remainder");
        power[0] = Double.POSITIVE_INFINITY;
        output.getFluidProvider()
            .flushCachedStackBig();
        output.getProvider()
            .flushCachedStackBig();
        check(
            output.getFluidProvider()
                .getCachedAmountBig()
                .signum() == 0,
            "output stayed in assembly cache");
        check(
            network.count(AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)))
                .equals(expected),
            "ME network received truncated water");
        check(
            network.count(AEItemStack.create(new ItemStack(Items.emerald)))
                .equals(BigInteger.ONE),
            "ME network received incorrect items");
        output.getFluidProvider()
            .flushCachedStackBig();
        check(
            network.count(AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)))
                .equals(expected),
            "second ME flush duplicated water");
    }

    private static void verifyRollback() throws ReflectiveOperationException {
        MTEPurificationUnitBase<?> unit = unit(1);
        prepare(unit);
        Fixture input = new Fixture(false);
        IAEStack<?> water = AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1));
        IAEStack<?> lava = AEFluidStack.create(new FluidStack(FluidRegistry.LAVA, 1));
        input.store(0, water, STOCK);
        input.store(1, lava, BigInteger.TEN);
        input.attach(unit, false);
        input.partialFluid();
        unit.startCycle(20, 0);
        check(unit.mMaxProgresstime == 0, "partial network debit started a cycle");
        check(
            input.count(water)
                .equals(STOCK)
                && input.count(lava)
                    .equals(BigInteger.TEN),
            "partial debit lost inputs");
        check(
            !input.logic()
                .isProcessing(),
            "failed cycle retained an input transaction");
    }

    private static void verifyLimitedWater() throws ReflectiveOperationException {
        MTEPurificationUnitBase<?> unit = unit(1);
        prepare(unit);
        Fixture input = new Fixture(false);
        IAEStack<?> water = AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1));
        IAEStack<?> lava = AEFluidStack.create(new FluidStack(FluidRegistry.LAVA, 1));
        input.store(0, water, BigInteger.valueOf(4_100_000));
        input.store(1, lava, BigInteger.TEN);
        input.attach(unit, false);
        unit.startCycle(20, 0);
        check(
            field(MTEPurificationUnitBase.class, "effectiveParallel").getInt(unit) == 2,
            "limited water did not reduce actual parallel");
        check(
            input.count(water)
                .equals(BigInteger.valueOf(100_000)),
            "boost spent water needed for the base recipe");
        MTEInfiniteMEOutputAssembly output = output(unit);
        unit.addRecipeOutputs();
        check(
            output.getFluidProvider()
                .getCachedAmountBig()
                .equals(BigInteger.valueOf(3_000_000)),
            "limited input produced excess water");
    }

    private static void verifyNativeWater() throws ReflectiveOperationException {
        MTEPurificationUnitBase<?> unit = unit(1);
        prepare(unit);
        unit.setMaxParallel(1);
        field(MTEPurificationUnitBase.class, "effectiveParallel").setInt(unit, 1);
        gregtech.api.metatileentity.implementations.MTEHatchInput water = new gregtech.api.metatileentity.implementations.MTEHatchInput(
            "apeiron.verify.water.physical",
            8,
            new String[0],
            null);
        gregtech.api.metatileentity.implementations.MTEHatchInput lava = new gregtech.api.metatileentity.implementations.MTEHatchInput(
            "apeiron.verify.water.auxiliary",
            8,
            new String[0],
            null);
        water.setBaseMetaTileEntity(new BaseMetaTileEntity());
        lava.setBaseMetaTileEntity(new BaseMetaTileEntity());
        water.mFluid = new FluidStack(FluidRegistry.WATER, 2_150_000);
        lava.mFluid = new FluidStack(FluidRegistry.LAVA, 3);
        unit.mInputHatches.add(water);
        unit.mInputHatches.add(lava);
        unit.startCycle(20, 0);
        check(
            (water.mFluid == null || water.mFluid.amount == 0) && (lava.mFluid == null || lava.mFluid.amount == 0),
            "native physical water or auxiliary debit changed");
        MTEInfiniteMEOutputAssembly output = output(unit);
        unit.addRecipeOutputs();
        check(
            output.getFluidProvider()
                .getCachedAmountBig()
                .equals(BigInteger.valueOf(1_500_000)),
            "native physical water output changed");
    }

    private static void verifyWirelessOutputs() throws ReflectiveOperationException {
        Field wireless;
        try {
            wireless = field(MTEPurificationUnitBase.class, "gtnl$wirelessMode");
        } catch (NoSuchFieldException absent) {
            return;
        }
        for (int kind : new int[] { 0, 6 }) {
            MTEPurificationUnitBase<?> unit = unit(kind);
            prepare(unit);
            Fixture input = new Fixture(false);
            input.store(0, AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)), STOCK);
            input.store(1, AEFluidStack.create(new FluidStack(FluidRegistry.LAVA, 1)), BigInteger.TEN);
            input.attach(unit, false);
            wireless.setBoolean(unit, true);
            field(MTEPurificationUnitBase.class, "gtnl$effectiveParallelLong").setLong(unit, PARALLEL);
            unit.startCycle(20, 0);
            successfulStage(unit, kind);
            MTEInfiniteMEOutputAssembly output = output(unit);
            unit.endCycle();
            BigInteger expected = BigInteger.valueOf(1_500_000L * PARALLEL * (kind == 6 ? 2 : 1));
            check(
                output.getFluidProvider()
                    .getCachedAmountBig()
                    .equals(expected),
                "GTNL wireless " + kind
                    + " output expected "
                    + expected
                    + " but was "
                    + output.getFluidProvider()
                        .getCachedAmountBig());
            verifyNetworkOutput(output, expected);
        }
    }

    private static void verifyFailedOutputs(boolean wireless) throws ReflectiveOperationException {
        Field mode;
        try {
            mode = field(MTEPurificationUnitBase.class, "gtnl$wirelessMode");
        } catch (NoSuchFieldException absent) {
            if (wireless) return;
            mode = null;
        }
        BigInteger expected = BigInteger.valueOf(1_500_000L * PARALLEL);
        for (int attempt = 0; attempt < 128; attempt++) {
            MTEPurificationUnitBase<?> unit = unit(0);
            prepare(unit);
            Fixture input = new Fixture(false);
            input.store(0, AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)), STOCK);
            input.store(1, AEFluidStack.create(new FluidStack(FluidRegistry.LAVA, 1)), BigInteger.TEN);
            input.attach(unit, false);
            if (wireless) {
                mode.setBoolean(unit, true);
                field(MTEPurificationUnitBase.class, "gtnl$effectiveParallelLong").setLong(unit, PARALLEL);
            }
            unit.startCycle(20, 0);
            NBTTagCompound saved = new NBTTagCompound();
            unit.saveNBTData(saved);
            MTEPurificationUnitBase<?> restored = unit(0);
            prepare(restored);
            restored.loadNBTData(saved);
            field(MTEPurificationUnitBase.class, "currentRecipeChance").setFloat(restored, -1.0f);
            MTEInfiniteMEOutputAssembly output = output(restored);
            restored.endCycle();
            BigInteger amount = output.getFluidProvider()
                .getCachedAmountBig();
            check(amount.signum() == 0 || amount.equals(expected), "failed cycle water was truncated to " + amount);
            check(
                output.getProvider()
                    .getCachedAmountBig()
                    .signum() == 0,
                "failed cycle produced success items");
            if (amount.signum() > 0) return;
        }
        throw new IllegalStateException("No degraded water observed in 128 failed cycles");
    }

    private static void verifyRepeatedOutputs() throws ReflectiveOperationException {
        MTEPurificationUnitBase<?> unit = unit(6);
        prepare(unit);
        Fixture input = new Fixture(false);
        input.store(0, AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)), STOCK);
        input.store(1, AEFluidStack.create(new FluidStack(FluidRegistry.LAVA, 1)), BigInteger.TEN);
        input.attach(unit, false);
        unit.startCycle(20, 0);
        successfulStage(unit, 6);
        MTEInfiniteMEOutputAssembly output = output(unit);
        unit.addRecipeOutputs();
        unit.addRecipeOutputs();
        BigInteger expected = BigInteger.valueOf(3_000_000L * PARALLEL);
        check(
            output.getFluidProvider()
                .getCachedAmountBig()
                .equals(expected),
            "repeated output duplicated water");
        check(
            output.getProvider()
                .getCachedAmountBig()
                .equals(BigInteger.ONE),
            "repeated output duplicated items");
        NBTTagCompound saved = new NBTTagCompound();
        unit.saveNBTData(saved);
        MTEPurificationUnitBase<?> restored = unit(6);
        prepare(restored);
        restored.loadNBTData(saved);
        MTEInfiniteMEOutputAssembly afterReload = output(restored);
        restored.endCycle();
        check(
            afterReload.getFluidProvider()
                .getCachedAmountBig()
                .signum() == 0,
            "reload released already produced water again");
        check(
            afterReload.getProvider()
                .getCachedAmountBig()
                .signum() == 0,
            "reload released already produced items again");
    }

    private static MTEInfiniteMEOutputAssembly output(MTEPurificationUnitBase<?> unit) {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(
            null,
            (short) ApeironConfig.getMachineId(ApeironMachines.MIXED_OUTPUT_ASSEMBLY_OFFSET));
        check(unit.addToMachineList(tile, 0), "purification output assembly registration failed");
        return (MTEInfiniteMEOutputAssembly) tile.getMetaTileEntity();
    }

    private static void verifyUltimatePlant() throws ReflectiveOperationException {
        GlobalEnergyWorldSavedData previous = GlobalEnergyWorldSavedData.INSTANCE;
        if (previous == null)
            GlobalEnergyWorldSavedData.INSTANCE = new GlobalEnergyWorldSavedData("apeiron.purification.verification");
        try {
            verifyFundedUltimatePlant(BigInteger.valueOf(PARALLEL), true, true);
            verifyFundedUltimatePlant(BigInteger.valueOf(5_000_000_000L), true, false);
            verifyFundedUltimatePlant(BigInteger.TEN.pow(24), true, true);
            verifyFundedUltimatePlant(BigInteger.TEN.pow(24), false, true);
            verifyUnlimitedAndBudget();
        } finally {
            GlobalEnergyWorldSavedData.INSTANCE = previous;
        }
    }

    private static void verifyFundedUltimatePlant(BigInteger parallel, boolean ultimate, boolean mixed)
        throws ReflectiveOperationException {
        MTEPurificationPlant plant = new MTEPurificationPlant("apeiron.verify.ultimate.water.plant");
        BaseMetaTileEntity base = new BaseMetaTileEntity();
        plant.setBaseMetaTileEntity(base);
        plant.mMachine = true;
        plant.mWrench = plant.mScrewdriver = plant.mSoftMallet = plant.mHardHammer = plant.mSolderingTool = plant.mCrowbar = true;
        UUID owner = UUID.randomUUID();
        BigInteger initialEnergy = BigInteger.TEN.pow(60);
        WirelessNetworkManager.strongCheckOrAddUser(owner);
        WirelessNetworkManager.setUserEU(owner, initialEnergy);
        ApeironMachineTile energy = new ApeironMachineTile();
        energy.setInitialValuesAsNBT(
            null,
            (short) ApeironConfig.getMachineId(
                ultimate ? ApeironMachines.ULTIMATE_ENERGY_HATCH_OFFSET
                    : ApeironMachines.INFINITE_ENERGY_HATCH_OFFSET));
        energy.setOwnerUuid(owner);
        plant.mEnergyHatches.add((MTEInfiniteEnergyHatch) energy.getMetaTileEntity());
        MTEPurificationUnitBase<?>[] units = new MTEPurificationUnitBase<?>[8];
        Fixture[] inputs = new Fixture[8];
        MTEInfiniteMEOutputAssembly[] outputs = new MTEInfiniteMEOutputAssembly[8];
        BigInteger power = BigInteger.ZERO;
        for (int kind = 0; kind < 8; kind++) {
            units[kind] = unit(kind);
            prepare(units[kind]);
            ((BigPurificationUnit) units[kind]).setPurificationParallelSettingBig(parallel);
            field(MTEPurificationUnitBase.class, "controller").set(units[kind], plant);
            units[kind].mMachine = true;
            inputs[kind] = new Fixture(mixed);
            inputs[kind].store(0, AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)), STOCK);
            inputs[kind].store(1, AEFluidStack.create(new FluidStack(FluidRegistry.LAVA, 1)), BigInteger.TEN);
            inputs[kind].store(2, AEItemStack.create(new ItemStack(Items.diamond)), BigInteger.valueOf(64));
            inputs[kind].attach(units[kind], mixed);
            if (!mixed && kind == 0) {
                gregtech.api.metatileentity.implementations.MTEHatchInputBus bus = new gregtech.api.metatileentity.implementations.MTEHatchInputBus(
                    "apeiron.verify.big.filter",
                    1,
                    new String[0],
                    null);
                bus.setBaseMetaTileEntity(new BaseMetaTileEntity());
                bus.mInventory[0] = new ItemStack(Items.diamond, 64);
                units[kind].mInputBusses.add(bus);
            }
            outputs[kind] = output(units[kind]);
            plant.registerLinkedUnit(units[kind]);
            power = power.add(
                BigInteger.valueOf(units[kind].getBasePowerUsage())
                    .multiply(parallel));
        }
        java.lang.reflect.Method start = MTEPurificationPlant.class.getDeclaredMethod("startCycle");
        start.setAccessible(true);
        start.invoke(plant);
        check(
            plant.lEUt == -power.min(BigInteger.valueOf(Long.MAX_VALUE))
                .longValue(),
            "plant compatibility power projection is incorrect");
        check(
            ((BigWirelessController) plant).getRecipeEUtBig()
                .equals(power),
            "plant exact power sum is incorrect");
        check(
            com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches.find(plant) != null,
            "ultimate plant energy hatch was not found");
        java.lang.reflect.Method usage = gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase.class
            .getDeclaredMethod("getActualEnergyUsage");
        usage.setAccessible(true);
        long actualUsage = (Long) usage.invoke(plant);
        check(
            actualUsage == power.min(BigInteger.valueOf(Long.MAX_VALUE))
                .longValue(),
            "plant power calculation overflow: " + actualUsage + " versus " + power);
        check(
            ((MTEInfiniteEnergyHatch) energy.getMetaTileEntity()).canConsumeEUBig(power),
            "ultimate hatch cannot use funded account");
        for (int kind = 0; kind < 8; kind++) {
            check(units[kind].mMaxProgresstime == plant.mMaxProgresstime, "plant did not start unit " + kind);
            check(
                ((BigWirelessController) units[kind]).getCurrentParallelsBig()
                    .equals(parallel),
                "unit " + kind + " actual parallel was truncated");
            check(
                inputs[kind].count(AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)))
                    .equals(
                        STOCK.subtract(
                            BigInteger.valueOf(2_150_000L)
                                .multiply(parallel))),
                "ultimate plant water debit mismatch for unit " + kind);
            check(
                inputs[kind].count(AEFluidStack.create(new FluidStack(FluidRegistry.LAVA, 1)))
                    .equals(BigInteger.valueOf(7)),
                "unit scaled its fixed auxiliary input");
            check(
                ((BigPurificationUnit) units[kind]).getPurificationWaterOutputBig()
                    .equals(
                        BigInteger.valueOf(1_500_000)
                            .multiply(parallel)),
                "unit preview output was truncated");
            NBTTagCompound hud = new NBTTagCompound();
            units[kind].saveNBTData(hud);
            check(
                hud.getString("ApeironActualWirelessEUt")
                    .equals(
                        BigInteger.valueOf(units[kind].getBasePowerUsage())
                            .multiply(parallel)
                            .toString()),
                "unit HUD energy was truncated or idle");
            successfulStage(units[kind], kind);
        }
        int ticks = plant.mMaxProgresstime;
        base.disableWorking();
        java.lang.reflect.Method tick = MTEPurificationPlant.class.getDeclaredMethod("updateCycleProgress");
        tick.setAccessible(true);
        for (int elapsed = 0; elapsed < ticks / 2; elapsed++) tick.invoke(plant);
        int resumedTicks = 0;
        if (parallel.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            NBTTagCompound savedPlant = new NBTTagCompound();
            plant.saveNBTData(savedPlant);
            check(
                savedPlant.getString("ApeironActualWirelessEUt")
                    .equals(power.toString()),
                "plant HUD energy was truncated or idle");
            MTEPurificationPlant restoredPlant = new MTEPurificationPlant("apeiron.verify.restored.water.plant");
            BaseMetaTileEntity restoredBase = new BaseMetaTileEntity();
            restoredBase.disableWorking();
            restoredPlant.setBaseMetaTileEntity(restoredBase);
            restoredPlant.loadNBTData(savedPlant);
            restoredPlant.mMachine = true;
            restoredPlant.mEnergyHatches.add((MTEInfiniteEnergyHatch) energy.getMetaTileEntity());
            check(
                ((BigWirelessController) restoredPlant).getRecipeEUtBig()
                    .equals(power),
                "reload lost full cycle power before units relinked");
            BigInteger beforeRelink = WirelessNetworkManager.getUserEU(owner);
            tick.invoke(restoredPlant);
            resumedTicks++;
            check(
                WirelessNetworkManager.getUserEU(owner)
                    .equals(beforeRelink.subtract(power)),
                "plant undercharged while restored units were still relinking");
            for (int kind = 0; kind < 8; kind++) {
                NBTTagCompound saved = new NBTTagCompound();
                units[kind].saveNBTData(saved);
                MTEPurificationUnitBase<?> restored = unit(kind);
                prepare(restored);
                restored.loadNBTData(saved);
                field(MTEPurificationUnitBase.class, "controller").set(restored, restoredPlant);
                restoredPlant.registerLinkedUnit(restored);
                units[kind] = restored;
                outputs[kind] = output(restored);
                check(
                    ((BigPurificationUnit) restored).getPurificationParallelSettingBig()
                        .equals(parallel),
                    "saved parallel setting was truncated");
                check(
                    ((BigPurificationUnit) restored).getPurificationParallelsBig()
                        .equals(parallel),
                    "saved cycle parallel was truncated");
                successfulStage(restored, kind);
            }
            plant = restoredPlant;
            check(
                ((BigWirelessController) plant).getRecipeEUtBig()
                    .equals(power),
                "reload lost exact plant power");
        }
        for (int elapsed = ticks / 2 + resumedTicks; elapsed < ticks; elapsed++) tick.invoke(plant);
        check(plant.mMaxProgresstime == 0, "ultimate plant cycle did not finish");
        check(
            WirelessNetworkManager.getUserEU(owner)
                .equals(initialEnergy.subtract(power.multiply(BigInteger.valueOf(ticks)))),
            "ultimate plant charged incorrect cycle energy");
        for (int kind = 0; kind < 8; kind++) {
            BigInteger expected = BigInteger.valueOf(1_500_000L * (kind == 6 ? 2 : 1))
                .multiply(parallel);
            check(
                outputs[kind].getFluidProvider()
                    .getCachedAmountBig()
                    .equals(expected),
                "ultimate plant output truncated for unit " + kind);
            check(units[kind].mMaxProgresstime == 0, "plant retained unit progress");
            verifyNetworkOutput(outputs[kind], expected);
        }
        Apeiron.LOG.info(
            "Purification plant verification passed: parallel={}, ultimate={}, mixed={}, all eight units, full water/EU debit, reload and ME output",
            parallel,
            ultimate,
            mixed);
    }

    private static void verifyUnlimitedAndBudget() throws ReflectiveOperationException {
        MTEPurificationPlant plant = new MTEPurificationPlant("apeiron.verify.water.budget");
        plant.setBaseMetaTileEntity(new BaseMetaTileEntity());
        plant.mMaxProgresstime = 20;
        plant.mEfficiency = 10000;
        UUID owner = UUID.randomUUID();
        MTEPurificationUnitBase<?> unit = unit(1);
        prepare(unit);
        field(MTEPurificationUnitBase.class, "controller").set(unit, plant);
        ApeironMachineTile energy = new ApeironMachineTile();
        energy.setInitialValuesAsNBT(
            null,
            (short) ApeironConfig.getMachineId(ApeironMachines.ULTIMATE_ENERGY_HATCH_OFFSET));
        energy.setOwnerUuid(owner);
        plant.mEnergyHatches.add((MTEInfiniteEnergyHatch) energy.getMetaTileEntity());
        BigInteger unitCost = BigInteger.valueOf(unit.getBasePowerUsage())
            .multiply(BigInteger.valueOf(20));
        WirelessNetworkManager.strongCheckOrAddUser(owner);
        WirelessNetworkManager.setUserEU(owner, unitCost.multiply(BigInteger.valueOf(7)));
        Fixture input = new Fixture(false);
        IAEStack<?> water = AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1));
        input.store(0, water, STOCK);
        input.store(1, AEFluidStack.create(new FluidStack(FluidRegistry.LAVA, 1)), BigInteger.TEN);
        input.attach(unit, false);
        ((BigPurificationUnit) unit).setPurificationParallelSettingBig(BigInteger.ZERO);
        check(
            ((BigWirelessController) unit).getParallelLimitBig()
                .isUnlimited(),
            "zero did not unlock parallel");
        unit.startCycle(20, 0);
        check(
            ((BigPurificationUnit) unit).getPurificationParallelsBig()
                .equals(BigInteger.valueOf(7)),
            "unlimited cycle ignored the full energy budget");
        plant.registerLinkedUnit(unit);
        MTEPurificationUnitBase<?> second = unit(1);
        prepare(second);
        field(MTEPurificationUnitBase.class, "controller").set(second, plant);
        ((BigPurificationUnit) second).setPurificationParallelSettingBig(BigInteger.ZERO);
        Fixture otherInput = new Fixture(false);
        otherInput.store(0, water, STOCK);
        otherInput.store(1, AEFluidStack.create(new FluidStack(FluidRegistry.LAVA, 1)), BigInteger.TEN);
        otherInput.attach(second, false);
        second.startCycle(20, 0);
        check(second.mMaxProgresstime == 0, "second unit overcommitted the same cycle energy");
        check(
            otherInput.count(water)
                .equals(STOCK),
            "unfunded unit consumed water");
        plant.mEnergyHatches.clear();
        check(
            !((BigWirelessController) unit).getParallelLimitBig()
                .isUnlimited(),
            "removed plant hatch kept unlocked limits");
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setInteger("configuredParallel", 12345);
        legacy.setInteger("effectiveParallel", 2345);
        second.loadNBTData(legacy);
        check(
            ((BigPurificationUnit) second).getPurificationParallelSettingBig()
                .equals(BigInteger.valueOf(12345)),
            "old save parallel setting was not migrated");
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void check(boolean okay, String message) {
        if (!okay) throw new IllegalStateException("Purification water: " + message);
    }
}
