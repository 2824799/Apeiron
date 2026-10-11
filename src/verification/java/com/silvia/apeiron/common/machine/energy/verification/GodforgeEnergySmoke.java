package com.silvia.apeiron.common.machine.energy.verification;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigGodforgeExoticModule;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.energy.WirelessControllerEnergy;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;

import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;
import tectech.thing.metaTileEntity.multi.godforge.MTEBaseModule;
import tectech.thing.metaTileEntity.multi.godforge.MTEExoticModule;
import tectech.thing.metaTileEntity.multi.godforge.MTEMoltenModule;
import tectech.thing.metaTileEntity.multi.godforge.MTEPlasmaModule;
import tectech.thing.metaTileEntity.multi.godforge.MTESmeltingModule;
import tectech.thing.metaTileEntity.multi.godforge.util.ForgeOfGodsData;
import tectech.thing.metaTileEntity.multi.godforge.util.GodforgeMath;

/** Real module logic, including the exotic randomized recipe generator which overflowed in player worlds. */
public final class GodforgeEnergySmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(30)
        .add(BigInteger.valueOf(13));
    private static final BigInteger BALANCE = BigInteger.TEN.pow(70);
    private static int maps;

    private GodforgeEnergySmoke() {}

    private static RecipeMap<?> map() {
        return RecipeMapBuilder.of("apeiron.verify.godforge." + maps++)
            .maxIO(1, 1, 0, 1)
            .build();
    }

    private static final class Smelting extends MTESmeltingModule {

        private final RecipeMap<?> recipes = map();

        private Smelting() {
            super("apeiron.verify.godforge.smelting");
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return recipes;
        }
    }

    private static final class Molten extends MTEMoltenModule {

        private final RecipeMap<?> recipes = map();

        private Molten() {
            super("apeiron.verify.godforge.molten");
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return recipes;
        }
    }

    private static final class Plasma extends MTEPlasmaModule {

        private final RecipeMap<?> recipes = map();

        private Plasma() {
            super("apeiron.verify.godforge.plasma");
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return recipes;
        }
    }

    public static void verify() {
        HashMap<UUID, BigInteger> oldEnergy = GlobalVariableStorage.GlobalEnergy;
        Map<UUID, UUID> oldTeams = SpaceProjectManager.spaceTeams;
        GlobalEnergyWorldSavedData oldSave = GlobalEnergyWorldSavedData.INSTANCE;
        try {
            GlobalVariableStorage.GlobalEnergy = new HashMap<>();
            SpaceProjectManager.spaceTeams = new HashMap<>();
            GlobalEnergyWorldSavedData.INSTANCE = new GlobalEnergyWorldSavedData();
            for (int kind = 0; kind < 3; kind++) for (boolean ultimate : new boolean[] { false, true })
                for (boolean upgrades : new boolean[] { false, true }) verifyModule(kind, ultimate, upgrades);
            for (boolean ultimate : new boolean[] { false, true })
                for (boolean magmatter : new boolean[] { false, true }) verifyExotic(ultimate, magmatter);
            verifyConfiguredExotic();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Godforge energy verification failed", error);
        } finally {
            GlobalVariableStorage.GlobalEnergy = oldEnergy;
            SpaceProjectManager.spaceTeams = oldTeams;
            GlobalEnergyWorldSavedData.INSTANCE = oldSave;
        }
        Apeiron.LOG.info(
            "Godforge energy verification passed: all modules, both hatches, main upgrades, absolute exotic parallel and overflow/reload, exact debit/output/tallies, voltage and time");
    }

    private static MTEBaseModule module(int kind, UUID owner, boolean upgrades) throws ReflectiveOperationException {
        MTEBaseModule module = kind == 0 ? new Smelting() : kind == 1 ? new Molten() : new Plasma();
        configure(module, owner, upgrades);
        module.getRecipeMap()
            .addRecipe(
                GTRecipeBuilder.builder()
                    .itemInputs(new ItemStack(Items.diamond))
                    .itemOutputs(new ItemStack(Items.emerald, 3))
                    .duration(80)
                    .eut(120)
                    .build()
                    .get());
        return module;
    }

    private static void configure(MTEBaseModule module, UUID owner, boolean upgrades)
        throws ReflectiveOperationException {
        module.setBaseMetaTileEntity(new BaseMetaTileEntity());
        field(MTEBaseModule.class, "userUUID").set(module, owner);
        module.setCalculatedMaxParallel(64);
        module.setProcessingVoltage(128);
        module.setHeat(10000);
        module.setHeatForOC(upgrades ? 1800 : 0);
        module.setSpeedBonus(upgrades ? 0.5 : 1);
        module.setEnergyDiscount(upgrades ? 0.8 : 1);
        module.setPlasmaTier(2);
        module.setMultiStepPlasma(true);
        module.setMagmatterCapable(true);
        module.mWrench = module.mScrewdriver = module.mSoftMallet = module.mHardHammer = module.mSolderingTool = module.mCrowbar = true;
    }

    private static MTEInfinitePatternInputAssembly attach(MTEBaseModule module, UUID owner, Boolean ultimate) {
        if (ultimate != null) check(
            module.addClassicToMachineList(
                tile(
                    ultimate ? ApeironMachines.ULTIMATE_ENERGY_HATCH_OFFSET
                        : ApeironMachines.INFINITE_ENERGY_HATCH_OFFSET,
                    owner),
                0),
            "module did not register energy hatch");
        check(
            module.addClassicToMachineList(tile(ApeironMachines.MIXED_OUTPUT_ASSEMBLY_OFFSET, owner), 0),
            "module did not register output assembly");
        MTEInfinitePatternInputAssembly input = (MTEInfinitePatternInputAssembly) tile(4, owner).getMetaTileEntity();
        module.mDualInputHatches.add(input);
        return input;
    }

    private static void verifyModule(int kind, boolean ultimate, boolean upgrades) throws ReflectiveOperationException {
        UUID owner = UUID.randomUUID();
        MTEBaseModule baseline = module(kind, owner, upgrades);
        baseline.setAlwaysMaxParallel(false);
        baseline.setPowerPanelMaxParallel(1);
        MTEInfinitePatternInputAssembly nativeInput = attach(baseline, owner, null);
        addItem(nativeInput, BigInteger.ONE);
        WirelessNetworkManager.setUserEU(owner, BALANCE);
        process(baseline, nativeInput);
        BigInteger perRecipe = baseline.getPowerTally();
        int nativeDuration = baseline.mMaxProgresstime;
        check(perRecipe.signum() > 0, "native cost fixture is empty");

        MTEBaseModule module = module(kind, owner, upgrades);
        MTEInfinitePatternInputAssembly input = attach(module, owner, ultimate);
        addItem(input, HUGE);
        WirelessRecipeState state = state(module);
        state.setParallelSettingBig(HUGE);
        state.setVoltageSetting(ultimate ? 8 : 128);
        state.setTargetDuration(7);
        check(
            InfiniteEnergyHatches.processingVoltage(module) == (ultimate ? Long.MAX_VALUE : 128)
                && module.getProcessingVoltage() == (ultimate ? Long.MAX_VALUE : 128),
            "module used the wrong normal/ultimate voltage policy");
        WirelessNetworkManager.setUserEU(owner, perRecipe.multiply(HUGE));
        process(module, input);
        check(
            state.isRunning() && state.getParallelsBig()
                .equals(HUGE),
            "module retained int parallel cap");
        check(module.mMaxProgresstime == (ultimate ? 7 : nativeDuration), "module lost speed or target duration");
        check(
            state.getTotalEUBig()
                .equals(perRecipe.multiply(HUGE)),
            "module lost main energy/heat discount");
        check(
            module.getPowerTally()
                .equals(state.getTotalEUBig()),
            "main controller lost power milestone tally");
        check(module.getRecipeTally() == Long.MAX_VALUE, "large recipe milestone tally overflowed");
        check(
            input.getBuffers()
                .get(0)
                .isEmpty(),
            "module did not consume exact inputs");
        finish(module, owner);
        check(
            output(module).getProvider()
                .getCachedAmountBig()
                .equals(HUGE.multiply(BigInteger.valueOf(3))),
            "module lost exact item output");
        check(module.getCalculatedMaxParallel() == 64 && module.getHeat() == 10000, "main upgrade values changed");
        addItem(input, BigInteger.ONE);
        if (ultimate) {
            state.setVoltageSetting(Long.MAX_VALUE);
            state.setTargetDuration(1);
        }
        WirelessNetworkManager.setUserEU(owner, perRecipe);
        process(module, input);
        check(
            state.getTotalEUBig()
                .equals(perRecipe),
            "ultimate time or old voltage changed per-recipe energy");
        finish(module, owner);
        check(module.getRecipeTally() == Long.MAX_VALUE, "next batch overflowed the saturated milestone tally");
        ForgeOfGodsData main = new ForgeOfGodsData();
        main.setTotalRecipesProcessed(100);
        BigInteger power = module.getPowerTally();
        GodforgeMath.queryMilestoneStats(module, main);
        check(
            main.getTotalRecipesProcessed() == Long.MAX_VALUE && main.getTotalPowerConsumed()
                .equals(power)
                && module.getRecipeTally() == 0
                && module.getPowerTally()
                    .signum() == 0,
            "main milestone aggregation overflowed");
        main.setTotalPowerConsumed(BigInteger.ONE.shiftLeft(200));
        GodforgeMath.determineChargeMilestone(main);
        GodforgeMath.determineConversionMilestone(main);
        check(
            Float.isFinite(main.getPowerMilestonePercentage()) && main.getPowerMilestonePercentage() > 1
                && main.getRecipeMilestonePercentage() > 1,
            "big milestones lost main controller progress");

        if (kind != 0 || ultimate) return; // Molten/plasma and ultimate hatches allow unlimited tier skips.
        MTEBaseModule low = module(kind, owner, upgrades);
        MTEInfinitePatternInputAssembly rejected = attach(low, owner, ultimate);
        addItem(rejected, BigInteger.ONE);
        state(low).setParallelSettingBig(BigInteger.ONE);
        state(low).setVoltageSetting(8);
        WirelessNetworkManager.setUserEU(owner, BALANCE);
        CheckRecipeResult result = attempt(low, rejected);
        check(
            !result.wasSuccessful() && !state(low).isRunning()
                && !rejected.getBuffers()
                    .get(0)
                    .isEmpty(),
            "low module voltage bypassed validation");
    }

    private static void verifyExotic(boolean ultimate, boolean magmatter) throws ReflectiveOperationException {
        UUID owner = UUID.randomUUID();
        MTEExoticModule module = new MTEExoticModule("apeiron.verify.godforge.exotic");
        configure(module, owner, true);
        module.setMagmatterMode(magmatter);
        module.setCalculatedMaxParallel(64);
        MTEInfinitePatternInputAssembly input = attach(module, owner, ultimate);
        WirelessRecipeState state = state(module);
        // Reproduce the original default which fed Integer.MAX_VALUE into the randomized recipe generator.
        check(
            state.getParallelSettingBig()
                .equals(BigInteger.valueOf(Integer.MAX_VALUE)),
            "default fixture changed");
        check(module.getActualParallel() == 1, "exotic generator retained main parallel multiplier");
        Method generate = method(
            MTEExoticModule.class,
            magmatter ? "generateMagmatterRecipe" : "generateQuarkGluonRecipe");
        GTRecipe recipe = (GTRecipe) generate.invoke(module);
        verifyExoticNoInput(ultimate, magmatter, recipe);
        // Minimal historical profiles omit exotic plasma registrations. The generator's output
        // is still tested; supply a registered fixture input for the transaction/debit checks.
        if (recipe.mFluidInputs.length == 0)
            recipe.mFluidInputs = new FluidStack[] { new FluidStack(FluidRegistry.WATER, 128) };
        check(recipe.mFluidOutputs[0].amount == (magmatter ? 576 : 1000), "generator did not produce one base output");
        // Compare against the untouched native whole-recipe charge, using the same randomized request.
        MTEExoticModule baseline = new MTEExoticModule("apeiron.verify.godforge.exotic.native");
        configure(baseline, owner, true);
        baseline.setMagmatterMode(magmatter);
        baseline.setProcessingVoltage(Integer.MAX_VALUE);
        MTEInfinitePatternInputAssembly nativeInput = attach(baseline, owner, null);
        field(MTEExoticModule.class, "actualParallel").setLong(baseline, 1);
        method(MTEExoticModule.class, "setPlasmaRecipe", GTRecipe.class).invoke(baseline, recipe);
        field(MTEExoticModule.class, "recipeInProgress").setBoolean(baseline, true);
        List<IAEStack<?>> nativeStocks = new ArrayList<>();
        for (FluidStack fluid : recipe.mFluidInputs) nativeStocks.add(AEFluidStack.create(fluid));
        check(nativeInput.addToBufferBig(0, nativeStocks), "exotic native input setup");
        WirelessNetworkManager.setUserEU(owner, BALANCE);
        process(baseline, nativeInput);
        BigInteger perBatch = baseline.getPowerTally();
        check(perBatch.signum() > 0, "exotic native energy fixture is empty");
        verifyExoticAbsoluteParallel(ultimate, magmatter, recipe, perBatch);
        method(MTEExoticModule.class, "setPlasmaRecipe", GTRecipe.class).invoke(module, recipe);
        field(MTEExoticModule.class, "recipeInProgress").setBoolean(module, true);
        state.setParallelSettingBig(HUGE);
        state.setVoltageSetting(Integer.MAX_VALUE);
        state.setTargetDuration(9);
        List<IAEStack<?>> stocks = new ArrayList<>();
        for (FluidStack fluid : recipe.mFluidInputs) stocks.add(
            BigAEStackValues.copyWithSize(
                AEFluidStack.create(fluid),
                BigInteger.valueOf(fluid.amount)
                    .multiply(HUGE)));
        check(input.addToBufferBig(0, stocks), "exotic input setup");
        WirelessNetworkManager.setUserEU(owner, perBatch.multiply(HUGE));
        module.mOutputBusses.clear();
        module.mOutputHatches.clear();
        CheckRecipeResult rejected = attempt(module, input);
        check(
            !rejected.wasSuccessful() && !state.isRunning()
                && !input.getBuffers()
                    .get(0)
                    .isEmpty()
                && WirelessNetworkManager.getUserEU(owner)
                    .equals(perBatch.multiply(HUGE)),
            "exotic capacity rejection consumed inputs or energy: ultimate=" + ultimate
                + " magmatter="
                + magmatter
                + " result="
                + rejected.getID()
                + " running="
                + state.isRunning()
                + " empty="
                + input.getBuffers()
                    .get(0)
                    .isEmpty()
                + " energy="
                + WirelessNetworkManager.getUserEU(owner)
                + " expected="
                + perBatch.multiply(HUGE)
                + " hatches="
                + com.silvia.apeiron.compat.OutputTransactions.hatches(module)
                    .size());
        check(
            module.addClassicToMachineList(tile(ApeironMachines.MIXED_OUTPUT_ASSEMBLY_OFFSET, owner), 0),
            "exotic output assembly restore failed");
        process(module, input);
        check(
            state.isRunning() && state.getParallelsBig()
                .equals(HUGE),
            "exotic batch did not consume big inputs");
        check(
            state.getTotalEUBig()
                .equals(perBatch.multiply(HUGE)),
            "exotic absolute parallel energy did not preserve native discounts");
        check(
            module.getPowerTally()
                .equals(state.getTotalEUBig()),
            "exotic lost power milestone tally");
        check(!ultimate || state.getDuration() == 9, "exotic ignored target completion time");
        BigInteger amount = HUGE.multiply(BigInteger.valueOf(magmatter ? 576 : 1000));
        check(
            BigAEStackValues.get(
                state.getOutputDisplay()
                    .get(0))
                .equals(amount),
            "exotic output did not use absolute parallel");
        check(
            input.getBuffers()
                .get(0)
                .isEmpty(),
            "exotic exact input deduction mismatch");
        NBTTagCompound saved = new NBTTagCompound();
        module.saveNBTData(saved);
        MTEExoticModule restored = new MTEExoticModule("apeiron.verify.godforge.exotic.restored");
        configure(restored, owner, true);
        attach(restored, owner, ultimate);
        restored.loadNBTData(saved);
        check(
            state(restored).getTotalEUBig()
                .equals(state.getTotalEUBig())
                && BigAEStackValues.get(
                    state(restored).getOutputDisplay()
                        .get(0))
                    .equals(amount),
            "exotic running reload lost state");
        WirelessNetworkManager.setUserEU(owner, state.getTotalEUBig());
        finish(restored, owner);
        check(
            output(restored).getFluidProvider()
                .getCachedAmountBig()
                .equals(amount),
            "exotic completion lost fluid output");
        // A cached recipe from the broken build can contain a negative int; recover its exact output identity.
        field(MTEExoticModule.class, "actualParallel").setLong(module, Integer.MAX_VALUE);
        recipe.mFluidOutputs[0].amount = -1000;
        module.setMagmatterMode(!magmatter);
        gregtech.api.logic.ProcessingLogic logic = (gregtech.api.logic.ProcessingLogic) field(
            MTEMultiBlockBase.class,
            "processingLogic").get(module);
        List<IAEStack<?>> recovered = ((com.silvia.apeiron.api.machine.parallel.BigRecipeOutputProvider) logic)
            .calculateRecipeOutputsBig(recipe, BigInteger.ONE);
        check(
            BigAEStackValues.get(recovered.get(0))
                .equals(BigInteger.valueOf(magmatter ? 576 : 1000)),
            "cached overflowed exotic output retained old parallel multiplier");
        check(
            ((BigGodforgeExoticModule) module).getExoticRecipeMultiplier()
                .equals(BigInteger.ONE),
            "cached exotic multiplier was not normalized");
    }

    private static void verifyExoticAbsoluteParallel(boolean ultimate, boolean magmatter, GTRecipe generated,
        BigInteger perRecipe) throws ReflectiveOperationException {
        for (int mainParallel : new int[] { 1, 1707 }) for (int parallel : new int[] { 1, 2 }) {
            UUID owner = UUID.randomUUID();
            MTEExoticModule module = new MTEExoticModule("apeiron.verify.godforge.exotic.absolute");
            configure(module, owner, true);
            module.setCalculatedMaxParallel(mainParallel);
            module.setMagmatterMode(magmatter);
            MTEInfinitePatternInputAssembly input = attach(module, owner, ultimate);
            WirelessRecipeState state = state(module);
            state.setParallelSettingBig(BigInteger.valueOf(parallel));
            state.setVoltageSetting(ultimate && parallel == 1 ? 8 : Integer.MAX_VALUE);
            state.setTargetDuration(parallel == 1 ? 3 : 11);
            GTRecipe cached = generated.copy();
            cached.mInputs = new ItemStack[0];
            cached.mFluidInputs = new FluidStack[0];
            cached.mFluidOutputs[0].amount = mainParallel * (magmatter ? 576 : 1000);
            field(MTEExoticModule.class, "actualParallel").setLong(module, mainParallel);
            method(MTEExoticModule.class, "setPlasmaRecipe", GTRecipe.class).invoke(module, cached);
            field(MTEExoticModule.class, "recipeInProgress").setBoolean(module, true);
            WirelessNetworkManager.setUserEU(owner, BALANCE);
            process(module, input);
            check(
                state.getParallelsBig()
                    .equals(BigInteger.valueOf(parallel)),
                "absolute exotic parallel count changed");
            check(
                state.getTotalEUBig()
                    .equals(perRecipe.multiply(BigInteger.valueOf(parallel))),
                "main parallel or ultimate voltage/time changed absolute exotic energy");
            BigInteger amount = BigInteger.valueOf((long) parallel * (magmatter ? 576 : 1000));
            check(
                BigAEStackValues.get(
                    state.getOutputDisplay()
                        .get(0))
                    .equals(amount),
                "main maximum was multiplied into absolute hatch parallel");
            check(module.getCalculatedMaxParallel() == mainParallel, "main parallel upgrade state changed");
            check(!ultimate || state.getDuration() == (parallel == 1 ? 3 : 11), "ultimate duration was ignored");
            WirelessNetworkManager.setUserEU(owner, state.getTotalEUBig());
            finish(module, owner);
            check(
                output(module).getFluidProvider()
                    .getCachedAmountBig()
                    .equals(amount),
                "absolute exotic completion used the old main multiplier");
            module.mEnergyHatches.clear();
            check(
                module.getActualParallel() == mainParallel
                    && ((BigGodforgeExoticModule) module).getExoticRecipeMultiplier()
                        .equals(BigInteger.valueOf(mainParallel)),
                "native module parallel did not restore after removing hatch");
        }
    }

    private static void verifyExoticNoInput(boolean ultimate, boolean magmatter, GTRecipe generated)
        throws ReflectiveOperationException {
        UUID owner = UUID.randomUUID();
        MTEExoticModule module = new MTEExoticModule("apeiron.verify.godforge.exotic.no_input");
        configure(module, owner, true);
        module.setMagmatterMode(magmatter);
        MTEInfinitePatternInputAssembly input = attach(module, owner, ultimate);
        WirelessRecipeState state = state(module);
        state.setParallelSettingBig(BigInteger.valueOf(3));
        state.setVoltageSetting(Integer.MAX_VALUE);
        state.setTargetDuration(9);
        GTRecipe recipe = generated.copy();
        recipe.mInputs = new ItemStack[0];
        recipe.mFluidInputs = new FluidStack[0];
        recipe.mFluidOutputs[0].amount = (magmatter ? 576 : 1000) * 64;
        field(MTEExoticModule.class, "actualParallel").setLong(module, 64);
        field(MTEExoticModule.class, "numberOfFluids").setInt(module, 0);
        field(MTEExoticModule.class, "numberOfItems").setInt(module, 0);
        field(MTEExoticModule.class, "randomizedFluidInput").set(module, new FluidStack[0]);
        field(MTEExoticModule.class, "randomizedItemInput").set(module, new ItemStack[0]);
        method(MTEExoticModule.class, "setPlasmaRecipe", GTRecipe.class).invoke(module, recipe);
        field(MTEExoticModule.class, "recipeInProgress").setBoolean(module, true);
        field(MTEExoticModule.class, "recipeRegenerated").setBoolean(module, false);
        WirelessNetworkManager.setUserEU(owner, BALANCE);
        process(module, input);
        check(
            state.isRunning() && state.getParallelsBig()
                .equals(BigInteger.valueOf(3)),
            "configured zero-input exotic recipe did not start");
        check(
            input.getBuffers()
                .get(0)
                .isEmpty(),
            "zero-input exotic recipe unexpectedly consumed an input");
        WirelessNetworkManager.setUserEU(owner, state.getTotalEUBig());
        finish(module, owner);
        // The native generator has inputs; recovery must persist this generated recipe,
        // rather than depending on an optional mod to generate another zero-input recipe.
        method(MTEExoticModule.class, "setPlasmaRecipe", GTRecipe.class).invoke(module, recipe);
        field(MTEExoticModule.class, "recipeInProgress").setBoolean(module, true);
        module.mOutputBusses.clear();
        module.mOutputHatches.clear();
        WirelessNetworkManager.setUserEU(owner, BALANCE);
        check(!attempt(module, input).wasSuccessful(), "zero-input output rejection fixture failed");
        NBTTagCompound saved = new NBTTagCompound();
        module.saveNBTData(saved);
        check(saved.hasKey("apeironExoticWaitingRecipe", 10), "zero-input waiting recipe was not saved");
        MTEExoticModule restored = new MTEExoticModule("apeiron.verify.godforge.no_input.reload");
        configure(restored, owner, true);
        MTEInfinitePatternInputAssembly restoredInput = attach(restored, owner, ultimate);
        restored.loadNBTData(saved);
        GTRecipe loaded = (GTRecipe) field(MTEExoticModule.class, "plasmaRecipe").get(restored);
        check(
            loaded != null && loaded.mFluidInputs.length == 0
                && loaded.mFluidOutputs[0].isFluidEqual(recipe.mFluidOutputs[0])
                && loaded.mDuration == recipe.mDuration
                && loaded.mEUt == recipe.mEUt,
            "zero-input waiting reload lost recipe identity or cost");
        process(restored, restoredInput);
        check(
            state(restored).getTotalEUBig()
                .equals(state.getTotalEUBig()),
            "waiting reload changed energy cost");
        WirelessNetworkManager.setUserEU(owner, state(restored).getTotalEUBig());
        finish(restored, owner);
        check(
            output(restored).getFluidProvider()
                .getCachedAmountBig()
                .equals(BigInteger.valueOf(3L * (magmatter ? 576 : 1000))),
            "zero-input waiting completion lost output");
    }

    private static void verifyConfiguredExotic() throws ReflectiveOperationException {
        Class<?> config;
        try {
            config = Class.forName("com.EyeOfHarmonyBuffer.Config.MainConfig");
        } catch (ClassNotFoundException absent) {
            return;
        }
        Field enabled = config.getField("ExoticModuleEnable");
        boolean previous = enabled.getBoolean(null);
        try {
            enabled.setBoolean(null, true);
            for (boolean ultimate : new boolean[] { false, true })
                for (boolean magmatter : new boolean[] { false, true }) {
                    UUID owner = UUID.randomUUID();
                    MTEExoticModule module = new MTEExoticModule("apeiron.verify.godforge.configured");
                    configure(module, owner, true);
                    module.setMagmatterMode(magmatter);
                    MTEInfinitePatternInputAssembly input = attach(module, owner, ultimate);
                    state(module).setParallelSettingBig(BigInteger.valueOf(3));
                    state(module).setTargetDuration(9);
                    GTRecipe generated = (GTRecipe) method(
                        MTEExoticModule.class,
                        magmatter ? "generateMagmatterRecipe" : "generateQuarkGluonRecipe").invoke(module);
                    check(
                        generated.mInputs.length == 0 && generated.mFluidInputs.length == 0,
                        "EOHB configuration did not generate a zero-input recipe");
                    WirelessNetworkManager.setUserEU(owner, BALANCE);
                    process(module, input);
                    check(state(module).isRunning(), "EOHB first zero-input recipe did not start");
                    WirelessNetworkManager.setUserEU(owner, state(module).getTotalEUBig());
                    finish(module, owner);
                    check(
                        output(module).getFluidProvider()
                            .getCachedAmountBig()
                            .equals(BigInteger.valueOf(3L * (magmatter ? 576 : 1000))),
                        "EOHB zero-input completion did not use absolute parallel");
                    module.mOutputBusses.clear();
                    module.mOutputHatches.clear();
                    WirelessNetworkManager.setUserEU(owner, BALANCE);
                    check(!attempt(module, input).wasSuccessful(), "missing output fixture was accepted");
                    NBTTagCompound saved = new NBTTagCompound();
                    module.saveNBTData(saved);
                    MTEExoticModule restored = new MTEExoticModule("apeiron.verify.godforge.configured.reload");
                    configure(restored, owner, true);
                    MTEInfinitePatternInputAssembly restoredInput = attach(restored, owner, ultimate);
                    restored.loadNBTData(saved);
                    process(restored, restoredInput);
                    check(state(restored).isRunning(), "EOHB zero-input waiting recipe did not resume after reload");
                    saved.removeTag("apeironExoticWaitingRecipe");
                    MTEExoticModule legacy = new MTEExoticModule("apeiron.verify.godforge.configured.legacy");
                    configure(legacy, owner, true);
                    MTEInfinitePatternInputAssembly legacyInput = attach(legacy, owner, ultimate);
                    legacy.loadNBTData(saved);
                    check(
                        !field(MTEExoticModule.class, "recipeInProgress").getBoolean(legacy),
                        "legacy empty waiting state was retained");
                    process(legacy, legacyInput);
                    check(state(legacy).isRunning(), "EOHB legacy zero-input recipe did not regenerate");
                }
        } finally {
            enabled.setBoolean(null, previous);
        }
    }

    private static void finish(MTEBaseModule module, UUID owner) {
        WirelessRecipeState state = state(module);
        for (int tick = 0; tick < state.getDuration(); tick++)
            check(module.onRunningTick(null), "module wireless debit failed");
        check(
            WirelessNetworkManager.getUserEU(owner)
                .signum() == 0,
            "module energy schedule did not debit exact total");
        WirelessControllerEnergy.complete(module);
        WirelessControllerEnergy.complete(module);
        check(
            !state.isRunning() && state.pending()
                .isEmpty(),
            "module retained completed outputs");
    }

    private static MTEInfiniteMEOutputAssembly output(MTEBaseModule module) {
        return (MTEInfiniteMEOutputAssembly) module.getOutputBusses()
            .get(0);
    }

    private static WirelessRecipeState state(MTEBaseModule module) {
        return ((BigWirelessController) module).getWirelessRecipeState();
    }

    private static void addItem(MTEInfinitePatternInputAssembly input, BigInteger count) {
        check(
            input.addToBufferBig(
                0,
                Arrays.asList(BigAEStackValues.copyWithSize(AEItemStack.create(new ItemStack(Items.diamond)), count))),
            "module input setup");
    }

    private static CheckRecipeResult attempt(MTEBaseModule module, MTEInfinitePatternInputAssembly input) {
        input.beginRecipeProcessing();
        try {
            return module.checkProcessing();
        } finally {
            input.endRecipeProcessing();
        }
    }

    private static void process(MTEBaseModule module, MTEInfinitePatternInputAssembly input) {
        CheckRecipeResult result = attempt(module, input);
        check(
            result.wasSuccessful(),
            module.getClass()
                .getSimpleName() + " rejected recipe: "
                + result.getID());
    }

    private static ApeironMachineTile tile(int offset, UUID owner) {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(offset));
        tile.setOwnerUuid(owner);
        return tile;
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> type, String name, Class<?>... args) throws ReflectiveOperationException {
        Method method = type.getDeclaredMethod(name, args);
        method.setAccessible(true);
        return method;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException("Godforge energy: " + message);
    }
}
