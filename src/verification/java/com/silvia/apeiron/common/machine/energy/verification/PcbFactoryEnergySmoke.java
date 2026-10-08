package com.silvia.apeiron.common.machine.energy.verification;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.me.output.verification.InfiniteMEOutputAssemblySmoke;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;

import appeng.util.item.AEItemStack;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.metadata.PCBFactoryTierKey;
import gregtech.api.recipe.metadata.PCBFactoryUpgrade;
import gregtech.api.recipe.metadata.PCBFactoryUpgradeKey;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeConstants;
import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;
import gregtech.common.tileentities.machines.multi.pcb.MTEPCBFactory;

/** Real PCB validation, trace-width output modifiers and wireless lifecycle on detached controllers. */
public final class PcbFactoryEnergySmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(40)
        .add(BigInteger.valueOf(17));
    private static int mapId;

    private PcbFactoryEnergySmoke() {}

    private static final class Factory extends MTEPCBFactory {

        private RecipeMap<?> recipes;

        Factory(GTRecipe recipe, int trace) {
            super("apeiron.verify.pcb");
            setBaseMetaTileEntity(new BaseMetaTileEntity());
            mWrench = mScrewdriver = mSoftMallet = mHardHammer = mSolderingTool = mCrowbar = true;
            setTraceSize(trace);
            recipes = RecipeMapBuilder.of("apeiron.verify.pcb." + mapId++)
                .maxIO(1, 1, 0, 1)
                .build();
            recipes.addRecipe(recipe);
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return recipes;
        }

        void complete() {
            outputAfterRecipe();
        }

        ProcessingLogic nativeProcess(ItemStack input) {
            ProcessingLogic logic = createProcessingLogic().setMachine(this)
                .setRecipeMap(recipes)
                .setInputItems(input)
                .setAvailableVoltage(32)
                .setAvailableAmperage(1);
            check(
                logic.process()
                    .wasSuccessful(),
                "native PCB processing failed");
            return logic;
        }
    }

    private static GTRecipe recipe(int tier, PCBFactoryUpgrade upgrade, Materials nanites) {
        return GTValues.RA.stdBuilder()
            .itemInputs(new ItemStack(Items.diamond, 2))
            .itemOutputs(new ItemStack(Items.emerald, 3))
            .fluidOutputs(Materials.Water.getFluid(9))
            .duration(20)
            .eut(30)
            .metadata(PCBFactoryTierKey.INSTANCE, tier)
            .metadata(PCBFactoryUpgradeKey.INSTANCE, upgrade)
            .metadata(GTRecipeConstants.PCB_NANITE_MATERIAL, nanites)
            .build()
            .get();
    }

    private static ApeironMachineTile tile(int offset) {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(offset));
        return tile;
    }

    private static WirelessRecipeState state(Factory factory) {
        return ((BigWirelessController) (Object) factory).getWirelessRecipeState();
    }

    private static UUID energy(Factory factory, boolean ultimate) {
        ApeironMachineTile energy = tile(
            ultimate ? ApeironMachines.ULTIMATE_ENERGY_HATCH_OFFSET : ApeironMachines.INFINITE_ENERGY_HATCH_OFFSET);
        UUID owner = UUID.randomUUID();
        energy.setOwnerUuid(owner);
        check(factory.addEnergyInputToMachineList(energy, 0), "PCB energy structure position rejected");
        state(factory).setVoltageSetting(32);
        state(factory).setParallelSettingBig(HUGE);
        state(factory).setTargetDuration(3);
        WirelessNetworkManager.setUserEU(owner, HUGE.multiply(BigInteger.valueOf(100000)));
        return owner;
    }

    private static MTEInfinitePatternInputAssembly input(Factory factory, BigInteger amount) {
        MTEInfinitePatternInputAssembly input = (MTEInfinitePatternInputAssembly) tile(
            ApeironMachines.PATTERN_INPUT_ASSEMBLY_OFFSET).getMetaTileEntity();
        check(
            input.addToBufferBig(
                0,
                Collections.singletonList(
                    BigAEStackValues.copyWithSize(AEItemStack.create(new ItemStack(Items.diamond)), amount))),
            "seed PCB input");
        factory.mDualInputHatches.add(input);
        return input;
    }

    private static MTEInfiniteMEOutputAssembly output(Factory factory) {
        MTEInfiniteMEOutputAssembly output = InfiniteMEOutputAssemblySmoke.assembly();
        check(factory.addToMachineList(output.getBaseMetaTileEntity(), 0), "register PCB output");
        return output;
    }

    private static CheckRecipeResult process(Factory factory, MTEInfinitePatternInputAssembly input) {
        input.beginRecipeProcessing();
        try {
            return factory.checkProcessing();
        } finally {
            input.endRecipeProcessing();
        }
    }

    private static void verifyControls(boolean ultimate, int trace) {
        Factory factory = new Factory(recipe(1, PCBFactoryUpgrade.NONE, null), trace);
        UUID owner = energy(factory, ultimate);
        int configuredDuration = trace == 50 ? 1 : 3;
        state(factory).setTargetDuration(configuredDuration);
        MTEInfiniteMEOutputAssembly output = output(factory);
        MTEInfinitePatternInputAssembly input = input(factory, HUGE.multiply(BigInteger.valueOf(2)));
        CheckRecipeResult result = process(factory, input);
        check(result.wasSuccessful(), "PCB recipe rejected: " + result.getID());
        check(
            state(factory).isRunning() && state(factory).getParallelsBig()
                .equals(HUGE),
            "PCB trace=" + trace + " bypassed exact parallel settings");
        int nativeDuration = trace == 50 ? 80 : trace == 200 ? 5 : 20;
        int duration = ultimate ? configuredDuration : nativeDuration;
        check(factory.mMaxProgresstime == duration, "PCB ignored configured/native work time");
        check(
            state(factory).getTotalEUBig()
                .equals(HUGE.multiply(BigInteger.valueOf(30L * nativeDuration))),
            "PCB trace-width base energy was discarded");
        check(
            input.getBuffers()
                .get(0)
                .isEmpty(),
            "PCB exact inputs not consumed");

        BigInteger expectedItems = state(factory).getOutputDisplay()
            .stream()
            .filter(s -> s instanceof appeng.api.storage.data.IAEItemStack)
            .map(BigAEStackValues::get)
            .reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger expectedFluids = state(factory).getOutputDisplay()
            .stream()
            .filter(s -> s instanceof appeng.api.storage.data.IAEFluidStack)
            .map(BigAEStackValues::get)
            .reduce(BigInteger.ZERO, BigInteger::add);
        if (trace == 200) {
            check(
                expectedItems.signum() > 0 && expectedItems.compareTo(HUGE.multiply(BigInteger.valueOf(3))) < 0
                    && expectedFluids.signum() > 0
                    && expectedFluids.compareTo(HUGE.multiply(BigInteger.valueOf(9))) < 0,
                "fractional PCB output multiplier ignored");
        } else {
            check(
                expectedItems.equals(HUGE.multiply(BigInteger.valueOf(trace == 50 ? 6 : 3)))
                    && expectedFluids.equals(HUGE.multiply(BigInteger.valueOf(trace == 50 ? 18 : 9))),
                "guaranteed PCB output multiplier ignored");
        }
        NBTTagCompound save = new NBTTagCompound();
        factory.saveNBTData(save);
        WirelessRecipeState reloaded = new WirelessRecipeState();
        reloaded.load(save.getCompoundTag("ApeironWirelessRecipe"));
        check(
            reloaded.getTotalEUBig()
                .equals(state(factory).getTotalEUBig()),
            "PCB energy lost on save");
        check(
            reloaded.getOutputDisplay()
                .stream()
                .map(BigAEStackValues::get)
                .reduce(BigInteger.ZERO, BigInteger::add)
                .equals(expectedItems.add(expectedFluids)),
            "PCB scaled outputs lost on save");

        BigInteger before = WirelessNetworkManager.getUserEU(owner);
        for (int tick = 0; tick < duration; tick++) check(factory.onRunningTick(null), "PCB wireless debit failed");
        check(
            WirelessNetworkManager.getUserEU(owner)
                .equals(before.subtract(state(factory).getTotalEUBig())),
            "PCB debit was rounded, duplicated or truncated");
        factory.complete();
        factory.complete();
        check(
            output.getProvider()
                .getCachedAmountBig()
                .equals(expectedItems),
            "PCB item multiplier/output lifecycle changed");
        check(
            output.getFluidProvider()
                .getCachedAmountBig()
                .equals(expectedFluids),
            "PCB fluid multiplier/output lifecycle changed");
    }

    private static void verifyNative() {
        Factory factory = new Factory(recipe(1, PCBFactoryUpgrade.NONE, null), 50);
        state(factory).setParallelSettingBig(HUGE);
        ItemStack input = new ItemStack(Items.diamond, 20);
        ProcessingLogic logic = factory.nativeProcess(input);
        check(
            !state(factory).isRunning() && logic.getCurrentParallels() == 1
                && logic.getDuration() == 80
                && logic.getCalculatedEut() == 30
                && input.stackSize == 18,
            "native PCB parallel/time/input/EU rules changed without an Apeiron hatch");
        check(
            logic.getOutputItems()[0].stackSize == 6 && logic.getOutputFluids()[0].amount == 18,
            "native PCB trace output rule changed");
    }

    private static void verifyRejection(GTRecipe recipe, boolean receiver, boolean funded) {
        Factory factory = new Factory(recipe, 50);
        UUID owner = energy(factory, true);
        if (receiver) output(factory);
        if (!funded) WirelessNetworkManager.setUserEU(owner, BigInteger.ONE);
        BigInteger amount = HUGE.multiply(BigInteger.valueOf(2));
        MTEInfinitePatternInputAssembly input = input(factory, amount);
        BigInteger before = WirelessNetworkManager.getUserEU(owner);
        check(!process(factory, input).wasSuccessful(), "PCB prerequisites/output/budget bypassed");
        check(
            !state(factory).isRunning() && input.getBuffers()
                .get(0)
                .getItemAmountBig()
                .equals(amount)
                && WirelessNetworkManager.getUserEU(owner)
                    .equals(before),
            "rejected PCB recipe spent resources");
    }

    public static void verify() {
        HashMap<UUID, BigInteger> oldEnergy = GlobalVariableStorage.GlobalEnergy;
        Map<UUID, UUID> oldTeams = SpaceProjectManager.spaceTeams;
        GlobalEnergyWorldSavedData oldSave = GlobalEnergyWorldSavedData.INSTANCE;
        try {
            GlobalVariableStorage.GlobalEnergy = new HashMap<>();
            SpaceProjectManager.spaceTeams = new HashMap<>();
            GlobalEnergyWorldSavedData.INSTANCE = new GlobalEnergyWorldSavedData();
            for (boolean ultimate : new boolean[] { false, true })
                for (int trace : new int[] { 100, 50, 200 }) verifyControls(ultimate, trace);
            verifyNative();
            verifyRejection(recipe(2, PCBFactoryUpgrade.NONE, null), true, true);
            verifyRejection(recipe(1, PCBFactoryUpgrade.BIO, null), true, true);
            verifyRejection(recipe(1, PCBFactoryUpgrade.NONE, Materials.Silver), true, true);
            verifyRejection(recipe(1, PCBFactoryUpgrade.NONE, null), false, true);
            verifyRejection(recipe(1, PCBFactoryUpgrade.NONE, null), true, false);
            Apeiron.LOG.info(
                "PCB factory verification passed: native trace modifiers, ordinary/ultimate controls, exact input/output/EU, save, tier/nanite/BIO prerequisites and rejection conservation");
        } finally {
            GlobalVariableStorage.GlobalEnergy = oldEnergy;
            SpaceProjectManager.spaceTeams = oldTeams;
            GlobalEnergyWorldSavedData.INSTANCE = oldSave;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
