package com.silvia.apeiron.common.integration.messtech;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigSpaceElevatorModule;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.energy.WirelessControllerEnergy;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.me.output.verification.InfiniteMEOutputAssemblySmoke;
import com.silvia.apeiron.common.machine.me.stocking.verification.StockingInputsSmoke.ItemBusFixture;
import com.silvia.apeiron.common.machine.parallel.NativeParallelPolicy;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;

import appeng.util.item.AEItemStack;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;
import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;
import gtnhintergalactic.recipe.SpacePumpingRecipes;
import gtnhintergalactic.tile.multi.elevator.TileEntitySpaceElevator;

/** Runs transformed, installed MessTech classes through their own recipe checks and scheduler. */
public final class MessTechIntegrationSmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(35)
        .add(BigInteger.valueOf(19));

    private MessTechIntegrationSmoke() {}

    private static final class Elevator extends TileEntitySpaceElevator {

        private long data;

        @Override
        public long getAvailableDataForModules() {
            return data;
        }

        private Elevator() {
            super("apeiron.verify.mess.parent");
        }
    }

    public static void verify() {
        HashMap<UUID, BigInteger> energy = GlobalVariableStorage.GlobalEnergy;
        Map<UUID, UUID> teams = SpaceProjectManager.spaceTeams;
        GlobalEnergyWorldSavedData save = GlobalEnergyWorldSavedData.INSTANCE;
        try {
            GlobalVariableStorage.GlobalEnergy = new HashMap<>();
            SpaceProjectManager.spaceTeams = new HashMap<>();
            GlobalEnergyWorldSavedData.INSTANCE = new GlobalEnergyWorldSavedData();
            ParallelHelper helper = (ParallelHelper) Class.forName("com.MessTech.common.machine.Base.MTParallelHelper")
                .getConstructor()
                .newInstance();
            check(NativeParallelPolicy.supports(helper), "MessTech default helper rejected");
            helper.setInputConsumer((recipe, count, fluids, items) -> {});
            check(!NativeParallelPolicy.supports(helper), "custom MessTech input consumer bypassed");
            for (String name : new String[] { "MTAssFactory", "MTHugeChemicalReactor", "MTChemicalTwister",
                "BosesCraftingArray", "MTDTPF" })
                for (boolean ultimate : new boolean[] { false, true }) ordinary(name, ultimate);
            for (boolean ultimate : new boolean[] { false, true }) {
                nano(ultimate);
                pump(ultimate);
                miner(ultimate);
                assembler(ultimate);
            }
            nanoAuxiliaries();
            naniteGates();
            apiaries();
            rejectsNativeGates();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("MessTech runtime verification failed", failure);
        } finally {
            GlobalVariableStorage.GlobalEnergy = energy;
            SpaceProjectManager.spaceTeams = teams;
            GlobalEnergyWorldSavedData.INSTANCE = save;
        }
        Apeiron.LOG.info(
            "MessTech verification passed: native processing families, both hatches, exact input/output/EU, native discounts, voltage/time, parallel threads, power pause, reload, four-slot pump, mining, assembler, all four apiaries, cooling/board/nanite gates and paid output retry");
    }

    private static MTEMultiBlockBase machine(String name) throws ReflectiveOperationException {
        MTEMultiBlockBase machine = (MTEMultiBlockBase) Class.forName("com.MessTech.common.machine." + name)
            .getConstructor(String.class)
            .newInstance("apeiron.verify.mess." + name);
        machine.setBaseMetaTileEntity(new BaseMetaTileEntity());
        machine.mWrench = machine.mScrewdriver = machine.mSoftMallet = machine.mHardHammer = machine.mSolderingTool = machine.mCrowbar = true;
        machine.mMachine = true;
        return machine;
    }

    private static ApeironMachineTile tile(int offset, UUID owner) {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(offset));
        tile.setOwnerUuid(owner);
        return tile;
    }

    private static WirelessRecipeState state(MTEMultiBlockBase machine) {
        return ((BigWirelessController) machine).getWirelessRecipeState();
    }

    private static void install(MTEMultiBlockBase machine, UUID owner, boolean ultimate) {
        check(
            machine.addToMachineList(
                tile(
                    ultimate ? ApeironMachines.ULTIMATE_ENERGY_HATCH_OFFSET
                        : ApeironMachines.INFINITE_ENERGY_HATCH_OFFSET,
                    owner),
                0),
            "MessTech energy hatch rejected");
        MTEInfiniteMEOutputAssembly output = InfiniteMEOutputAssemblySmoke.assembly();
        check(machine.addToMachineList(output.getBaseMetaTileEntity(), 0), "MessTech output assembly rejected");
        state(machine).setParallelSettingBig(HUGE);
        state(machine).setVoltageSetting(30);
        state(machine).setTargetDuration(3);
        WirelessNetworkManager.setUserEU(owner, BigInteger.TEN.pow(90));
    }

    private static GTRecipe recipe(ItemStack item, ItemStack output, int ticks) {
        return new GTRecipe(
            false,
            new ItemStack[] { item },
            new ItemStack[] { output },
            null,
            null,
            null,
            null,
            null,
            new FluidStack[0],
            new FluidStack[0],
            ticks,
            30,
            0);
    }

    private static void configure(MTEMultiBlockBase machine) throws ReflectiveOperationException {
        String name = machine.getClass()
            .getSimpleName();
        if (name.equals("MTChemicalTwister")) MessTechAccess.set(machine, "mStructureLevel", 2);
        if (name.equals("MTDTPF")) {
            Class<?> tier = Class.forName("com.MessTech.common.machine.MTDTPF$LevelTier");
            MessTechAccess.set(machine, "levelTier", Enum.valueOf((Class) tier, "TIER5"));
            MessTechAccess.set(machine, "running_time", 20L * 3600);
        }
    }

    private static void ordinary(String name, boolean ultimate) throws ReflectiveOperationException {
        MTEMultiBlockBase machine = machine(name);
        UUID owner = UUID.randomUUID();
        install(machine, owner, ultimate);
        configure(machine);
        GTRecipe recipe = recipe(new ItemStack(Items.stick), new ItemStack(Items.emerald, 7), 400);
        if (name.equals("MTDTPF"))
            recipe.mFluidInputs = new FluidStack[] { new FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 1),
                new FluidStack(net.minecraftforge.fluids.FluidRegistry.LAVA, 1) };
        RecipeMap<?> map = machine.getRecipeMap();
        map.addRecipe(recipe);
        MTEInfinitePatternInputAssembly input = (MTEInfinitePatternInputAssembly) tile(
            ApeironMachines.PATTERN_INPUT_ASSEMBLY_OFFSET,
            owner).getMetaTileEntity();
        appeng.api.storage.data.IAEItemStack stock = BigAEStackValues
            .copyWithSize(AEItemStack.create(recipe.mInputs[0]), HUGE.add(BigInteger.ONE));
        List<appeng.api.storage.data.IAEStack<?>> stocks = new ArrayList<>();
        stocks.add(stock);
        for (FluidStack fluid : recipe.mFluidInputs) stocks
            .add(BigAEStackValues.copyWithSize(appeng.util.item.AEFluidStack.create(fluid), HUGE.add(BigInteger.ONE)));
        check(input.addToBufferBig(0, stocks), "MessTech input setup failed");
        machine.mDualInputHatches.add(input);
        try {
            input.beginRecipeProcessing();
            CheckRecipeResult result;
            try {
                result = machine.checkProcessing();
            } finally {
                input.endRecipeProcessing();
            }
            check(result.wasSuccessful(), name + " rejected recipe: " + result.getDisplayString());
            WirelessRecipeState state = state(machine);
            check(
                state.isRunning() && state.getParallelsBig()
                    .equals(HUGE),
                name + " parallel truncated: running=" + state.isRunning() + ", parallel=" + state.getParallelsBig());
            check(
                input.getBuffers()
                    .get(0)
                    .getAmountBig(stock)
                    .equals(BigInteger.ONE),
                name + " input debit changed");
            ProcessingLogic logic = (ProcessingLogic) MessTechAccess.get(machine, "processingLogic");
            OverclockCalculator calculator = (OverclockCalculator) MessTechAccess
                .call(logic, "createOverclockCalculator", recipe);
            calculator.setEUt(30)
                .setParallel(1)
                .setCurrentParallel(1)
                .setAmperage(1)
                .setAmperageOC(false)
                .setNoOverclock(true)
                .calculate();
            BigInteger expected = BigInteger.valueOf(calculator.getConsumption())
                .multiply(BigInteger.valueOf(Math.max(1, calculator.getDuration())))
                .multiply(HUGE);
            check(
                state.getTotalEUBig()
                    .equals(expected),
                name + " native discount or speed changed: " + state.getTotalEUBig() + " / " + expected);
            check(
                machine.mMaxProgresstime == (ultimate ? 3 : Math.max(1, calculator.getDuration())),
                name + " time setting ignored");
            check(
                sum(state.getOutputsUnsorted()).equals(HUGE.multiply(BigInteger.valueOf(7))),
                name + " output truncated");
            NBTTagCompound saved = new NBTTagCompound();
            machine.saveNBTData(saved);
            MTEMultiBlockBase restored = machine(name);
            restored.loadNBTData(saved);
            check(
                state(restored).getTotalEUBig()
                    .equals(expected),
                name + " recipe lost on reload");
            BigInteger before = WirelessNetworkManager.getUserEU(owner);
            for (int tick = 0; tick < state.getDuration(); tick++)
                check(WirelessControllerEnergy.debitTick(machine), name + " power debit failed");
            check(
                before.subtract(WirelessNetworkManager.getUserEU(owner))
                    .equals(expected),
                name + " double or missing debit");
            WirelessControllerEnergy.complete(machine);
            check(
                !state.isRunning() && state.pending()
                    .isEmpty(),
                name + " completed outputs stuck");
        } finally {
            map.getBackend()
                .removeRecipe(recipe);
        }
    }

    private static void nano(boolean ultimate) throws ReflectiveOperationException {
        MTEMultiBlockBase machine = machine("MTNanoScaleFoundry");
        UUID owner = UUID.randomUUID();
        install(machine, owner, ultimate);
        state(machine).setTargetDuration(2);
        GTRecipe recipe = recipe(new ItemStack(Items.stick), new ItemStack(Items.emerald, 9), 200);
        Object[] threads = ((Collection<?>) MessTechAccess.call(machine, "getThreads")).toArray();
        Object a = threads[0], b = threads[2];
        RecipeMap<?> mapA = (RecipeMap<?>) MessTechAccess.call(a, "getRecipeMap");
        RecipeMap<?> mapB = (RecipeMap<?>) MessTechAccess.call(b, "getRecipeMap");
        mapA.addRecipe(recipe);
        mapB.addRecipe(recipe);
        ItemBusFixture inputA = new ItemBusFixture(AEItemStack.create(recipe.mInputs[0]), HUGE);
        ItemBusFixture inputB = new ItemBusFixture(AEItemStack.create(recipe.mInputs[0]), HUGE);
        machine.mInputBusses.add(inputA.bus);
        machine.mInputBusses.add(inputB.bus);
        Map<Object, Integer> circuits = (Map<Object, Integer>) MessTechAccess.get(machine, "inputBusCircuitNumbers");
        circuits.put(inputA.bus, 1);
        circuits.put(inputB.bus, 3);
        MessNanoTasks tasks = ((MessNanoController) machine).getMessNanoTasks();
        try {
            inputA.begin();
            inputB.begin();
            try {
                CheckRecipeResult first = (CheckRecipeResult) MessTechAccess
                    .call(machine, "checkAndStartThread", machine.getBaseMetaTileEntity(), a);
                CheckRecipeResult second = (CheckRecipeResult) MessTechAccess
                    .call(machine, "checkAndStartThread", machine.getBaseMetaTileEntity(), b);
                check(
                    first.wasSuccessful() && second.wasSuccessful(),
                    "native nano thread start failed: " + first.getDisplayString() + "/" + second.getDisplayString());
            } finally {
                inputA.end();
                inputB.end();
            }
            check(
                tasks.parallels()
                    .equals(HUGE.multiply(BigInteger.valueOf(2))),
                "nano big per-thread parallel lost");
            check(
                inputA.count()
                    .signum() == 0
                    && inputB.count()
                        .signum() == 0,
                "nano isolated input debit changed");
            check(
                sum(
                    tasks.products()
                        .snapshotOutputsUnsorted()).equals(HUGE.multiply(BigInteger.valueOf(18))),
                "nano outputs truncated");
            int duration = ((Number) MessTechAccess.call(a, "getMaxProgressTime")).intValue();
            int[] nativeTime = (int[]) MessTechAccess
                .call(machine, "calculateOverclockedTask", a, recipe, new ArrayList<FluidStack>(), 30L);
            check(duration == (ultimate ? 2 : nativeTime[1]), "nano native/ultimate time changed: " + duration);
            if (cpw.mods.fml.common.FMLCommonHandler.instance()
                .getSide()
                .isClient()) {
                Object gui = MessTechAccess.call(machine, "getGui");
                com.cleanroommc.modularui.value.sync.PanelSyncManager sync = new com.cleanroommc.modularui.value.sync.PanelSyncManager(
                    new com.cleanroommc.modularui.value.sync.ModularSyncManager(false),
                    true);
                MessTechAccess.call(gui, "registerSyncValues", sync);
                com.cleanroommc.modularui.value.sync.StringSyncValue exact = sync.findSyncHandler(
                    "apeiron_threadParallel0",
                    com.cleanroommc.modularui.value.sync.StringSyncValue.class);
                check(
                    exact.getStringValue()
                        .equals(com.silvia.apeiron.math.BigNumberFormatter.formatCompact(HUGE)),
                    "nano GUI thread amount truncated");
                check(
                    MessTechAccess.call(gui, "createThreadStatusWidget", sync) != null,
                    "nano exact GUI rows failed to build");
            }
            BigInteger expectedEU = tasks.energy();
            WirelessNetworkManager.setUserEU(owner, BigInteger.ZERO);
            MessTechAccess.call(machine, "tickThread", machine.getBaseMetaTileEntity(), a, 0L);
            check(
                ((Number) MessTechAccess.call(a, "getProgressTime")).intValue() == 0 && tasks.isActive(),
                "nano power pause lost task");
            WirelessNetworkManager.setUserEU(owner, expectedEU);
            MessTechAccess.call(machine, "tickThread", machine.getBaseMetaTileEntity(), a, 1L);
            MessTechAccess.call(machine, "tickThread", machine.getBaseMetaTileEntity(), b, 1L);
            BigInteger unpaid = WirelessNetworkManager.getUserEU(owner);
            NBTTagCompound saved = new NBTTagCompound();
            machine.saveNBTData(saved);
            MTEMultiBlockBase restored = machine("MTNanoScaleFoundry");
            install(restored, owner, ultimate);
            restored.loadNBTData(saved);
            MessNanoTasks loaded = ((MessNanoController) restored).getMessNanoTasks();
            check(
                loaded.parallels()
                    .equals(tasks.parallels())
                    && loaded.energy()
                        .equals(expectedEU),
                "nano ledger lost on reload");
            Object[] restoredThreads = ((Collection<?>) MessTechAccess.call(restored, "getThreads")).toArray();
            Object ra = restoredThreads[0], rb = restoredThreads[2];
            WirelessNetworkManager.setUserEU(owner, unpaid);
            java.util.List<?> receivers = new ArrayList<>(restored.mOutputBusses);
            restored.mOutputBusses.clear();
            restored.mOutputHatches.clear();
            for (int i = 1; i < duration; i++) {
                MessTechAccess.call(restored, "tickThread", restored.getBaseMetaTileEntity(), ra, (long) i);
                MessTechAccess.call(restored, "tickThread", restored.getBaseMetaTileEntity(), rb, (long) i);
            }
            check(
                loaded.isActive() && loaded.eut()
                    .signum() == 0
                    && WirelessNetworkManager.getUserEU(owner)
                        .signum() == 0,
                "nano output retry lost a paid task or charged it again");
            restored.mOutputBusses.addAll((java.util.Collection) receivers);
            MessTechAccess.call(restored, "tickThread", restored.getBaseMetaTileEntity(), ra, 0L);
            MessTechAccess.call(restored, "tickThread", restored.getBaseMetaTileEntity(), rb, 0L);
            check(
                !loaded.isActive() && WirelessNetworkManager.getUserEU(owner)
                    .signum() == 0,
                "nano recovery or energy conservation failed");
            check(
                sum(
                    state(restored).pending()
                        .snapshotOutputsUnsorted()).equals(HUGE.multiply(BigInteger.valueOf(18))),
                "nano exact completion lost output");
            MessTechAccess.call(restored, "tickThread", restored.getBaseMetaTileEntity(), ra, 0L);
            check(
                sum(
                    state(restored).pending()
                        .snapshotOutputsUnsorted()).equals(HUGE.multiply(BigInteger.valueOf(18))),
                "nano completed task emitted twice");
        } finally {
            mapA.getBackend()
                .removeRecipe(recipe);
            mapB.getBackend()
                .removeRecipe(recipe);
        }
    }

    private static void pump(boolean ultimate) throws ReflectiveOperationException {
        MTEMultiBlockBase machine = machine("module.SpaceModulePumpInfinity");
        TileEntitySpaceElevator parent = new Elevator();
        parent.setBaseMetaTileEntity(new BaseMetaTileEntity());
        UUID owner = UUID.randomUUID();
        install(parent, owner, ultimate);
        MessTechAccess.set(parent, "motorTier", 5);
        MessTechAccess.call(machine, "connect", parent);
        MTEInfiniteMEOutputAssembly output = InfiniteMEOutputAssemblySmoke.assembly();
        machine.addToMachineList(output.getBaseMetaTileEntity(), 0);
        state(machine).setParallelSettingBig(HUGE);
        state(machine).setTargetDuration(2);
        state(machine).setVoltageSetting(Long.MAX_VALUE);
        Pair<Integer, Integer> key = SpacePumpingRecipes.RECIPES.keySet()
            .iterator()
            .next();
        Arrays.fill((int[]) MessTechAccess.get(machine, "planetTypes"), key.getLeft());
        Arrays.fill((int[]) MessTechAccess.get(machine, "gasTypes"), key.getRight());
        BigInteger before = WirelessNetworkManager.getUserEU(owner);
        CheckRecipeResult result = (CheckRecipeResult) MessTechAccess.call(machine, "checkProcessing_EM");
        check(result.wasSuccessful(), "MessTech pump rejected parent hatch");
        BigInteger actual = state(machine).getParallelsBig();
        check(actual.equals(HUGE.multiply(BigInteger.valueOf(4))), "pump slot parallels changed");
        check(before.equals(WirelessNetworkManager.getUserEU(owner)), "pump charged native lump and Apeiron ledger");
        check(machine.supportsPowerPanel(), "MessTech module energy controls hidden");
        check(
            ((BigSpaceElevatorModule) machine).getModuleParallelLimitBig()
                .getBound()
                .get()
                .equals(HUGE.multiply(BigInteger.valueOf(4))),
            "pump aggregate cap incorrect");
        check(machine.mMaxProgresstime == (ultimate ? 2 : 23), "pump time ignored");
        check(
            sum(state(machine).getOutputsUnsorted())
                .equals(actual.multiply(BigInteger.valueOf(SpacePumpingRecipes.RECIPES.get(key).amount))),
            "pump outputs truncated");
        BigInteger cost = state(machine).getTotalEUBig();
        for (int i = 0; i < state(machine).getDuration(); i++)
            check(WirelessControllerEnergy.debitTick(machine), "pump debit failed");
        check(
            before.subtract(WirelessNetworkManager.getUserEU(owner))
                .equals(cost),
            "pump EU debit mismatch");
        MessTechAccess.call(machine, "disconnect");
        check(InfiniteEnergyHatches.find(machine) == null, "disconnected module kept parent hatch");
    }

    private static void miner(boolean ultimate) throws ReflectiveOperationException {
        MTEMultiBlockBase machine = machine("module.SpaceModuleMinerInfinity");
        Elevator parent = new Elevator();
        parent.setBaseMetaTileEntity(new BaseMetaTileEntity());
        UUID owner = UUID.randomUUID();
        install(parent, owner, ultimate);
        parent.data = Long.MAX_VALUE;
        MessTechAccess.set(parent, "motorTier", 5);
        MessTechAccess.call(machine, "connect", parent);
        machine.addToMachineList(
            InfiniteMEOutputAssemblySmoke.assembly()
                .getBaseMetaTileEntity(),
            0);
        BigInteger parallels = BigInteger.valueOf(5_000_000_000L);
        state(machine).setParallelSettingBig(parallels);
        state(machine).setTargetDuration(3);
        state(machine).setVoltageSetting(Long.MAX_VALUE);
        MessTechAccess.call(machine, "setCrossRecipeParallel", 2);
        MessTechAccess.call(machine, "setDistance", 0);
        GTRecipe recipe = gregtech.api.util.GTRecipeBuilder.builder()
            .itemInputs(new ItemStack(Items.diamond))
            .itemOutputs(new ItemStack(Items.emerald, 3))
            .duration(20)
            .eut(30)
            .metadata(
                gtnhintergalactic.recipe.IGRecipeMaps.SPACE_MINING_DATA,
                new gtnhintergalactic.recipe.SpaceMiningData("apeiron.verify.mess", 0, 1, 1, 1, 10, 1))
            .build()
            .get();
        RecipeMap<?> map = machine.getRecipeMap();
        map.addRecipe(recipe);
        ItemBusFixture items = new ItemBusFixture(
            AEItemStack.create(recipe.mInputs[0]),
            parallels.multiply(BigInteger.valueOf(2)));
        com.silvia.apeiron.common.machine.me.stocking.verification.StockingInputsSmoke.FluidHatchFixture plasma = new com.silvia.apeiron.common.machine.me.stocking.verification.StockingInputsSmoke.FluidHatchFixture(
            appeng.util.item.AEFluidStack.create(gregtech.api.enums.Materials.Plutonium241.getPlasma(1)),
            parallels.multiply(BigInteger.valueOf(300)));
        machine.mInputBusses.add(items.bus);
        parent.mInputHatches.add(plasma.hatch);
        BigInteger before = WirelessNetworkManager.getUserEU(owner);
        try {
            items.begin();
            CheckRecipeResult result;
            try {
                result = (CheckRecipeResult) MessTechAccess.call(machine, "checkProcessing_EM");
            } finally {
                items.end();
            }
            check(result.wasSuccessful(), "MessTech miner rejected exact recipe: " + result.getDisplayString());
            check(
                state(machine).getParallelsBig()
                    .equals(parallels.multiply(BigInteger.valueOf(2))),
                "miner cross recipe parallel changed");
            check(
                items.count()
                    .signum() == 0
                    && plasma.count()
                        .signum() == 0,
                "miner item/shared plasma debit truncated");
            check(
                ((Number) MessTechAccess.get(machine, "eRequiredData")).longValue() == 100_000_000_000L,
                "miner computation truncated");
            check(
                sum(state(machine).getOutputsUnsorted()).equals(parallels.multiply(BigInteger.valueOf(6))),
                "miner exact ore output changed");
            check(before.equals(WirelessNetworkManager.getUserEU(owner)), "miner charged native lump twice");
            check(machine.mMaxProgresstime == (ultimate ? 3 : 21), "miner time ignored");
            BigInteger cost = state(machine).getTotalEUBig();
            for (int i = 0; i < state(machine).getDuration(); i++)
                check(WirelessControllerEnergy.debitTick(machine), "miner power debit failed");
            check(
                before.subtract(WirelessNetworkManager.getUserEU(owner))
                    .equals(cost),
                "miner cost not conserved");
            GTRecipe weighted = recipe(new ItemStack(Items.diamond), new ItemStack(Items.emerald), 20);
            weighted.mOutputs = new ItemStack[] { new ItemStack(Items.emerald), new ItemStack(Items.diamond) };
            weighted.mOutputChances = new int[] { 5000, 5000 };
            gtnhintergalactic.recipe.SpaceMiningData data = recipe
                .getMetadata(gtnhintergalactic.recipe.IGRecipeMaps.SPACE_MINING_DATA);
            check(
                sum(
                    MessMiningRecipes.outputs(machine, weighted, data, HUGE, 5)
                        .snapshotOutputsUnsorted()).equals(HUGE),
                "weighted mining rolls not conserved");
            java.util.Set<String> filter = (java.util.Set<String>) MessTechAccess.get(machine, "configuredOres");
            filter.add((String) MessTechAccess.call(machine, "getOreString", weighted.mOutputs[0]));
            MessTechAccess.set(machine, "isWhitelisted", true);
            BigInteger filtered = sum(
                MessMiningRecipes.outputs(machine, weighted, data, HUGE, 5)
                    .snapshotOutputsUnsorted());
            check(filtered.signum() > 0 && filtered.compareTo(HUGE) < 0, "miner filter rerolled rejected outputs");
        } finally {
            map.getBackend()
                .removeRecipe(recipe);
        }
    }

    private static void assembler(boolean ultimate) throws ReflectiveOperationException {
        MTEMultiBlockBase machine = machine("module.SpaceModuleAssemblerInfinity");
        Elevator parent = new Elevator();
        parent.setBaseMetaTileEntity(new BaseMetaTileEntity());
        UUID owner = UUID.randomUUID();
        install(parent, owner, ultimate);
        MessTechAccess.set(parent, "motorTier", 5);
        MessTechAccess.call(machine, "connect", parent);
        machine.addToMachineList(
            InfiniteMEOutputAssemblySmoke.assembly()
                .getBaseMetaTileEntity(),
            0);
        state(machine).setParallelSettingBig(HUGE);
        state(machine).setVoltageSetting(30);
        state(machine).setTargetDuration(2);
        GTRecipe recipe = recipe(new ItemStack(Items.stick), new ItemStack(Items.emerald, 5), 20);
        RecipeMap<?> map = machine.getRecipeMap();
        map.addRecipe(recipe);
        ItemBusFixture input = new ItemBusFixture(
            AEItemStack.create(recipe.mInputs[0]),
            HUGE.multiply(BigInteger.valueOf(2)));
        machine.mInputBusses.add(input.bus);
        BigInteger before = WirelessNetworkManager.getUserEU(owner);
        try {
            input.begin();
            CheckRecipeResult result;
            try {
                result = (CheckRecipeResult) MessTechAccess.call(machine, "checkProcessing_EM");
            } finally {
                input.end();
            }
            check(
                result.wasSuccessful(),
                "MessTech assembler rejected native processing: " + result.getDisplayString());
            check(
                state(machine).getParallelsBig()
                    .equals(HUGE.multiply(BigInteger.valueOf(2)))
                    && input.count()
                        .signum() == 0,
                "assembler cross-recipe exact debit changed");
            check(
                sum(state(machine).getOutputsUnsorted()).equals(HUGE.multiply(BigInteger.TEN)),
                "assembler output truncated");
            check(before.equals(WirelessNetworkManager.getUserEU(owner)), "assembler charged native lump twice");
            check(machine.mMaxProgresstime == (ultimate ? 2 : 1), "assembler native/ultimate time ignored");
            BigInteger cost = state(machine).getTotalEUBig();
            for (int i = 0; i < state(machine).getDuration(); i++)
                check(WirelessControllerEnergy.debitTick(machine), "assembler power debit failed");
            check(
                before.subtract(WirelessNetworkManager.getUserEU(owner))
                    .equals(cost),
                "assembler power accounting changed");
        } finally {
            map.getBackend()
                .removeRecipe(recipe);
        }
    }

    private static void nanoAuxiliaries() throws ReflectiveOperationException {
        MTEMultiBlockBase machine = machine("MTNanoScaleFoundry");
        UUID owner = UUID.randomUUID();
        install(machine, owner, true);
        state(machine).setParallelSettingBig(BigInteger.valueOf(2));
        state(machine).setTargetDuration(1);
        Object thread = MessTechAccess.call(machine, "getThread", "OpticalOrganizer");
        GTRecipe recipe = recipe(new ItemStack(Items.stick), new ItemStack(Items.emerald), 40);
        recipe.mFluidInputs = new FluidStack[] { gregtech.api.enums.Materials.Grade3PurifiedWater.getFluid(50) };
        RecipeMap<?> map = (RecipeMap<?>) MessTechAccess.call(thread, "getRecipeMap");
        map.addRecipe(recipe);
        ItemBusFixture items = new ItemBusFixture(AEItemStack.create(recipe.mInputs[0]), BigInteger.valueOf(2));
        machine.mInputBusses.add(items.bus);
        ((Map<Object, Integer>) MessTechAccess.get(machine, "inputBusCircuitNumbers")).put(items.bus, 9);
        com.silvia.apeiron.common.machine.me.stocking.verification.StockingInputsSmoke.FluidHatchFixture water3 = new com.silvia.apeiron.common.machine.me.stocking.verification.StockingInputsSmoke.FluidHatchFixture(
            appeng.util.item.AEFluidStack.create(gregtech.api.enums.Materials.Grade3PurifiedWater.getFluid(1)),
            BigInteger.valueOf(1000));
        com.silvia.apeiron.common.machine.me.stocking.verification.StockingInputsSmoke.FluidHatchFixture water4 = new com.silvia.apeiron.common.machine.me.stocking.verification.StockingInputsSmoke.FluidHatchFixture(
            appeng.util.item.AEFluidStack.create(gregtech.api.enums.Materials.Grade4PurifiedWater.getFluid(1)),
            BigInteger.valueOf(1000));
        machine.mInputHatches.add(water3.hatch);
        try {
            items.begin();
            water3.begin();
            try {
                check(
                    !((CheckRecipeResult) MessTechAccess
                        .call(machine, "checkAndStartThread", machine.getBaseMetaTileEntity(), thread)).wasSuccessful(),
                    "optical thread bypassed two-water gate");
            } finally {
                items.end();
                water3.end();
            }
            check(
                items.count()
                    .equals(BigInteger.valueOf(2)),
                "optical rejection consumed input");
            machine.mInputHatches.add(water4.hatch);
            items.begin();
            water3.begin();
            water4.begin();
            try {
                check(
                    ((CheckRecipeResult) MessTechAccess
                        .call(machine, "checkAndStartThread", machine.getBaseMetaTileEntity(), thread)).wasSuccessful(),
                    "optical exact task failed");
            } finally {
                items.end();
                water3.end();
                water4.end();
            }
            check(
                water3.count()
                    .equals(BigInteger.valueOf(420))
                    && water4.count()
                        .equals(BigInteger.valueOf(520)),
                "optical recipe/cooling debit not conserved");
            MessTechAccess.call(machine, "tickThread", machine.getBaseMetaTileEntity(), thread, 0L);
            check(
                !((MessNanoController) machine).getMessNanoTasks()
                    .isActive(),
                "optical one tick task did not finish");
        } finally {
            map.getBackend()
                .removeRecipe(recipe);
        }

        machine = machine("MTNanoScaleFoundry");
        install(machine, owner, true);
        state(machine).setTargetDuration(1);
        thread = MessTechAccess.call(machine, "getThread", "BoardProcessor");
        Object tank = MessTechAccess.call(machine, "getBoardTank", thread),
            chamber = MessTechAccess.call(tank, "getTank", 1);
        MessTechAccess.set(chamber, "storedFluid", MessTechAccess.call(machine, "boardTankFluid", 1));
        MessTechAccess.set(chamber, "impurityFluid", MessTechAccess.call(machine, "boardTankImpurity", 1));
        MessTechAccess.set(chamber, "fluidAmount", 1_000_000);
        MessTechAccess.set(chamber, "processedItems", 17);
        recipe = recipe(new ItemStack(Items.stick), new ItemStack(Items.emerald, 2), 40);
        map = (RecipeMap<?>) MessTechAccess.call(thread, "getRecipeMap");
        map.addRecipe(recipe);
        items = new ItemBusFixture(AEItemStack.create(recipe.mInputs[0]), HUGE);
        machine.mInputBusses.add(items.bus);
        ((Map<Object, Integer>) MessTechAccess.get(machine, "inputBusCircuitNumbers")).put(items.bus, 4);
        try {
            items.begin();
            try {
                check(
                    ((CheckRecipeResult) MessTechAccess
                        .call(machine, "checkAndStartThread", machine.getBaseMetaTileEntity(), thread)).wasSuccessful(),
                    "board exact task failed");
            } finally {
                items.end();
            }
            MessTechAccess.call(machine, "tickThread", machine.getBaseMetaTileEntity(), thread, 0L);
            check(
                ((Number) MessTechAccess.get(chamber, "impurityAmount")).intValue() == 1_000_000,
                "board big production did not increase impurity");
            check(
                ((Number) MessTechAccess.get(chamber, "processedItems")).intValue()
                    == HUGE.multiply(BigInteger.valueOf(2))
                        .add(BigInteger.valueOf(17))
                        .mod(BigInteger.valueOf(1000))
                        .intValue(),
                "board counter overflowed");
        } finally {
            map.getBackend()
                .removeRecipe(recipe);
        }
    }

    private static void naniteGates() throws ReflectiveOperationException {
        MTEMultiBlockBase machine = machine("BosesCraftingArray");
        UUID owner = UUID.randomUUID();
        install(machine, owner, true);
        state(machine).setParallelSettingBig(BigInteger.ONE);
        state(machine).setTargetDuration(2);
        GTRecipe recipe = gregtech.api.util.GTRecipeBuilder.builder()
            .itemInputs(new ItemStack(Items.stick))
            .itemOutputs(new ItemStack(Items.emerald))
            .duration(400)
            .eut(30)
            .metadata(
                gregtech.api.util.GTRecipeConstants.NANITE_TIERS,
                new gregtech.api.enums.NaniteTier[] { gregtech.api.enums.NaniteTier.Carbon,
                    gregtech.api.enums.NaniteTier.Gold })
            .build()
            .get();
        RecipeMap<?> map = machine.getRecipeMap();
        map.addRecipe(recipe);
        ItemBusFixture items = new ItemBusFixture(AEItemStack.create(recipe.mInputs[0]), BigInteger.ONE);
        machine.mInputBusses.add(items.bus);
        try {
            items.begin();
            try {
                check(
                    machine.checkProcessing()
                        .wasSuccessful(),
                    "nanite exact recipe did not start");
            } finally {
                items.end();
            }
            BigInteger before = WirelessNetworkManager.getUserEU(owner);
            check(WirelessControllerEnergy.debitTick(machine), "nanite stall caused shutdown");
            MessTechAccess.call(machine, "incrementProgressTime");
            check(
                machine.mProgresstime == 0 && before.equals(WirelessNetworkManager.getUserEU(owner)),
                "missing nanites advanced or consumed energy");
            NBTTagCompound saved = new NBTTagCompound();
            machine.saveNBTData(saved);
            MTEMultiBlockBase restored = machine("BosesCraftingArray");
            install(restored, owner, true);
            restored.loadNBTData(saved);
            check(!((MessNaniteController) restored).canAdvanceNanites(), "nanite gate lost on reload");
            gregtech.api.metatileentity.implementations.MTEHatchNanite hatch = new gregtech.api.metatileentity.implementations.MTEHatchNanite(
                "apeiron.verify.nanites",
                new String[0],
                null,
                12,
                10000);
            hatch.setBaseMetaTileEntity(new BaseMetaTileEntity());
            hatch.setItemStack(gregtech.api.enums.NaniteTier.Gold.getStack());
            hatch.setItemCount(64);
            ((List<Object>) MessTechAccess.get(restored, "naniteHatches")).add(hatch);
            check(((MessNaniteController) restored).canAdvanceNanites(), "valid nanites did not unlock recipe");
            for (int i = 0; i < 2; i++) {
                check(WirelessControllerEnergy.debitTick(restored), "nanite EU failed");
                MessTechAccess.call(restored, "incrementProgressTime");
            }
            check(restored.mProgresstime == 2, "nanite speed bypassed configured time");
        } finally {
            map.getBackend()
                .removeRecipe(recipe);
        }
    }

    private static void apiaries() throws ReflectiveOperationException {
        forestry.api.apiculture.IBeeRoot original = forestry.api.apiculture.BeeManager.beeRoot;
        forestry.api.apiculture.IBeekeepingMode mode = original.getBeekeepingModes()
            .get(0);
        forestry.api.apiculture.BeeManager.beeRoot = (forestry.api.apiculture.IBeeRoot) java.lang.reflect.Proxy
            .newProxyInstance(
                original.getClass()
                    .getClassLoader(),
                new Class<?>[] { forestry.api.apiculture.IBeeRoot.class },
                (proxy, method, args) -> {
                    if (method.getName()
                        .equals("getBeekeepingMode") && args != null
                        && args[0] instanceof net.minecraft.world.World) return mode;
                    return method.invoke(original, args);
                });
        try {
            ItemStack queen = original.getMemberStack(
                original.templateAsIndividual(original.getDefaultTemplate()),
                forestry.api.apiculture.EnumBeeType.QUEEN.ordinal());
            check(queen != null, "apiary queen fixture missing");
            for (int mk = 1; mk <= 4; mk++) for (boolean ultimate : new boolean[] { false, true }) {
                MTEMultiBlockBase machine = machine("module.SpaceModuleApiary$MK" + mk);
                Elevator parent = new Elevator();
                parent.setBaseMetaTileEntity(new BaseMetaTileEntity());
                UUID owner = UUID.randomUUID();
                install(parent, owner, ultimate);
                MessTechAccess.set(parent, "motorTier", 5);
                MessTechAccess.call(machine, "connect", parent);
                machine.addToMachineList(
                    InfiniteMEOutputAssemblySmoke.assembly()
                        .getBaseMetaTileEntity(),
                    0);
                ((BaseMetaTileEntity) machine.getBaseMetaTileEntity()).setWorldObj(allocate(BeeWorld.class));
                state(machine).setParallelSettingBig(HUGE);
                state(machine).setVoltageSetting(Long.MAX_VALUE);
                state(machine).setTargetDuration(2);
                ItemStack[] slots = (ItemStack[]) MessTechAccess.call(machine, "getBeeSlots");
                slots[0] = queen.copy();
                ItemStack[] nativeDrops = (ItemStack[]) MessTechAccess.call(machine, "runBees", 1);
                long base = 0;
                for (ItemStack drop : nativeDrops) base += drop.stackSize;
                check(base > 0, "native apiary produced no fixture drops");
                BigInteger before = WirelessNetworkManager.getUserEU(owner);
                CheckRecipeResult result = (CheckRecipeResult) MessTechAccess.call(machine, "checkProcessing_EM");
                check(result.wasSuccessful(), "apiary rejected parent hatch");
                check(
                    state(machine).getParallelsBig()
                        .equals(HUGE),
                    "apiary parallel truncated");
                check(
                    sum(state(machine).getOutputsUnsorted()).equals(HUGE.multiply(BigInteger.valueOf(base))),
                    "apiary genome/tier production changed");
                check(ItemStack.areItemStacksEqual(queen, slots[0]), "apiary queen was consumed");
                check(machine.mMaxProgresstime == (ultimate ? 2 : 100), "apiary time setting ignored");
                check(before.equals(WirelessNetworkManager.getUserEU(owner)), "apiary charged native lump twice");
                BigInteger cost = state(machine).getTotalEUBig();
                for (int i = 0; i < state(machine).getDuration(); i++)
                    check(WirelessControllerEnergy.debitTick(machine), "apiary energy failed");
                check(
                    before.subtract(WirelessNetworkManager.getUserEU(owner))
                        .equals(cost),
                    "apiary energy not conserved");
            }
        } finally {
            forestry.api.apiculture.BeeManager.beeRoot = original;
        }
    }

    private static final class BeeWorld extends net.minecraft.world.World {

        private BeeWorld() {
            super(null, "apeiron.verify.bees", (net.minecraft.world.WorldSettings) null, null, null);
        }

        @Override
        public net.minecraft.world.chunk.Chunk getChunkFromChunkCoords(int x, int z) {
            return new net.minecraft.world.chunk.Chunk(this, x, z);
        }

        @Override
        protected net.minecraft.world.chunk.IChunkProvider createChunkProvider() {
            return null;
        }

        @Override
        protected int func_152379_p() {
            return 0;
        }

        @Override
        public net.minecraft.entity.Entity getEntityByID(int id) {
            return null;
        }
    }

    private static <T> T allocate(Class<T> type) throws ReflectiveOperationException {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field field = unsafe.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(
            unsafe.getMethod("allocateInstance", Class.class)
                .invoke(field.get(null), type));
    }

    private static void rejectsNativeGates() throws ReflectiveOperationException {
        MTEMultiBlockBase machine = machine("module.SpaceModuleMinerInfinity");
        check(
            !((CheckRecipeResult) MessTechAccess.call(machine, "checkProcessing_EM")).wasSuccessful(),
            "miner bypassed parent motor gate");
        machine = machine("module.SpaceModuleApiary$MK1");
        check(MessModuleRecipes.apiary(machine) == null, "module without Apeiron hatch changed behavior");
    }

    private static BigInteger sum(List<appeng.api.storage.data.IAEStack<?>> outputs) {
        return outputs.stream()
            .map(BigAEStackValues::get)
            .reduce(BigInteger.ZERO, BigInteger::add);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
