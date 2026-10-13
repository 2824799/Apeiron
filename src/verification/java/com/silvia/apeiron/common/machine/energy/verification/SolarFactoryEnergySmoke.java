package com.silvia.apeiron.common.machine.energy.verification;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.metadata.SolarFactoryRecipeDataKey;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.recipe.SolarFactoryRecipeData;
import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;
import gregtech.common.tileentities.machines.multi.MTESolarFactory;

/** Real solar processing logic: wafer-dependent input adjustment and post-parallel output bonuses. */
public final class SolarFactoryEnergySmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(40)
        .add(BigInteger.valueOf(17));
    private static int mapId;

    private SolarFactoryEnergySmoke() {}

    private static final class Factory extends MTESolarFactory {

        private RecipeMap<?> recipes;

        Factory(GTRecipe recipe, int tier) {
            super("apeiron.verify.solar");
            setBaseMetaTileEntity(new BaseMetaTileEntity());
            mWrench = mScrewdriver = mSoftMallet = mHardHammer = mSolderingTool = mCrowbar = true;
            try {
                java.lang.reflect.Field field = MTESolarFactory.class.getDeclaredField("mTier");
                field.setAccessible(true);
                field.setInt(this, tier);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
            recipes = RecipeMapBuilder.of("apeiron.verify.solar." + mapId++)
                .maxIO(2, 1, 0, 1)
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

        ProcessingLogic nativeProcess(ItemStack[] inputs) {
            ProcessingLogic logic = createProcessingLogic().setMachine(this)
                .setRecipeMap(recipes)
                .setInputItems(inputs)
                .setAvailableVoltage(32)
                .setAvailableAmperage(1);
            check(
                logic.process()
                    .wasSuccessful(),
                "native solar recipe rejected");
            return logic;
        }
    }

    private static GTRecipe recipe(boolean wafer, int requiredTier) {
        gregtech.api.util.GTRecipeBuilder builder = GTValues.RA.stdBuilder()
            .itemInputs(new ItemStack(Items.diamond, 2))
            .itemOutputs(new ItemStack(Items.emerald, 3))
            .fluidOutputs(Materials.Water.getFluid(7))
            .duration(20)
            .eut(30);
        if (wafer) builder.metadata(SolarFactoryRecipeDataKey.INSTANCE, new SolarFactoryRecipeData(1, 3, requiredTier));
        return builder.build()
            .get();
    }

    private static ItemStack wafer(int tier, int amount) {
        return GTUtility.copyAmountUnsafe(
            amount,
            MTESolarFactory.validWafers.get(tier - 1)
                .getLeft());
    }

    private static ApeironMachineTile tile(int offset) {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(offset));
        return tile;
    }

    private static WirelessRecipeState state(Factory factory) {
        return ((BigWirelessController) (Object) factory).getWirelessRecipeState();
    }

    private static UUID energy(Factory factory, boolean ultimate, BigInteger parallel) {
        ApeironMachineTile energy = tile(
            ultimate ? ApeironMachines.ULTIMATE_ENERGY_HATCH_OFFSET : ApeironMachines.INFINITE_ENERGY_HATCH_OFFSET);
        UUID owner = UUID.randomUUID();
        energy.setOwnerUuid(owner);
        check(factory.addEnergyInputToMachineList(energy, 0), "solar energy hatch registration failed");
        state(factory).setVoltageSetting(32);
        state(factory).setParallelSettingBig(parallel);
        state(factory).setTargetDuration(3);
        WirelessNetworkManager.setUserEU(owner, HUGE.multiply(BigInteger.valueOf(100000)));
        return owner;
    }

    private static MTEInfinitePatternInputAssembly input(Factory factory, BigInteger diamonds, int tier,
        BigInteger wafers) {
        MTEInfinitePatternInputAssembly input = (MTEInfinitePatternInputAssembly) tile(
            ApeironMachines.PATTERN_INPUT_ASSEMBLY_OFFSET).getMetaTileEntity();
        List<IAEStack<?>> values = new ArrayList<>();
        values.add(BigAEStackValues.copyWithSize(AEItemStack.create(new ItemStack(Items.diamond)), diamonds));
        if (wafers.signum() > 0) values.add(BigAEStackValues.copyWithSize(AEItemStack.create(wafer(tier, 1)), wafers));
        check(input.addToBufferBig(0, values), "seed solar inputs");
        factory.mDualInputHatches.add(input);
        return input;
    }

    private static MTEInfiniteMEOutputAssembly output(Factory factory) {
        MTEInfiniteMEOutputAssembly output = InfiniteMEOutputAssemblySmoke.assembly();
        check(factory.addToMachineList(output.getBaseMetaTileEntity(), 0), "solar output registration failed");
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

    private static BigInteger itemOutputs(WirelessRecipeState state) {
        return state.getOutputDisplay()
            .stream()
            .filter(s -> s instanceof IAEItemStack)
            .map(BigAEStackValues::get)
            .reduce(BigInteger.ZERO, BigInteger::add);
    }

    private static void verifyControls(boolean ultimate, int tier, int waferTier, boolean needsWafer) {
        Factory factory = new Factory(recipe(needsWafer, tier), tier);
        UUID owner = energy(factory, ultimate, HUGE);
        MTEInfiniteMEOutputAssembly output = output(factory);
        BigInteger wafers = needsWafer ? HUGE.multiply(BigInteger.valueOf(3)) : BigInteger.ZERO;
        MTEInfinitePatternInputAssembly input = input(factory, HUGE.multiply(BigInteger.valueOf(2)), waferTier, wafers);
        CheckRecipeResult result = process(factory, input);
        check(result.wasSuccessful(), "solar recipe failed: " + result.getID());
        check(
            state(factory).getParallelsBig()
                .equals(HUGE),
            "solar exact parallel count lost");
        check(
            input.getBuffers()
                .get(0)
                .isEmpty(),
            "solar recipe did not consume its additional wafers");
        int quarters = !needsWafer || tier == 1 ? 4 : Math.min(8, 4 + (waferTier - 1) * (tier == 3 ? 2 : 1));
        BigInteger expected = HUGE.multiply(BigInteger.valueOf(3L * quarters))
            .divide(BigInteger.valueOf(4));
        check(itemOutputs(state(factory)).equals(expected), "solar native capped/rounded wafer bonus lost");
        int duration = ultimate ? 3 : 20;
        check(factory.mMaxProgresstime == duration, "solar energy hatch time ignored");
        check(
            state(factory).getTotalEUBig()
                .equals(HUGE.multiply(BigInteger.valueOf(600))),
            "solar bonus incorrectly changed recipe energy");
        NBTTagCompound save = new NBTTagCompound();
        factory.saveNBTData(save);
        WirelessRecipeState restored = new WirelessRecipeState();
        restored.load(save.getCompoundTag("ApeironWirelessRecipe"));
        check(itemOutputs(restored).equals(expected), "solar adjusted outputs lost on reload");
        BigInteger before = WirelessNetworkManager.getUserEU(owner);
        for (int tick = 0; tick < duration; tick++) check(factory.onRunningTick(null), "solar debit failed");
        check(
            WirelessNetworkManager.getUserEU(owner)
                .equals(before.subtract(state(factory).getTotalEUBig())),
            "solar exact energy debit changed");
        factory.complete();
        factory.complete();
        check(
            output.getProvider()
                .getCachedAmountBig()
                .equals(expected),
            "solar output lost or duplicated");
        check(
            output.getFluidProvider()
                .getCachedAmountBig()
                .equals(HUGE.multiply(BigInteger.valueOf(7))),
            "solar wafer bonus changed fluid products");
    }

    private static void verifyNative() {
        Factory factory = new Factory(recipe(true, 2), 2);
        state(factory).setParallelSettingBig(HUGE);
        ItemStack diamonds = new ItemStack(Items.diamond, 8), wafers = wafer(2, 12);
        ProcessingLogic logic = factory.nativeProcess(new ItemStack[] { diamonds, wafers });
        check(
            !state(factory).isRunning() && logic.getCurrentParallels() == 1
                && logic.getDuration() == 20
                && logic.getCalculatedEut() == 30
                && diamonds.stackSize == 6
                && wafers.stackSize == 9,
            "solar native parallel/time/input/energy changed without hatch");
        check(
            logic.getOutputItems()[0].stackSize == 3 && logic.getOutputFluids()[0].amount == 7,
            "solar native post-parallel floor changed");
    }

    private static void verifyWaferLimit() {
        Factory factory = new Factory(recipe(true, 2), 2);
        energy(factory, true, HUGE);
        output(factory);
        BigInteger available = HUGE.subtract(BigInteger.ONE);
        MTEInfinitePatternInputAssembly input = input(
            factory,
            HUGE.multiply(BigInteger.valueOf(2)),
            2,
            available.multiply(BigInteger.valueOf(3))
                .add(BigInteger.valueOf(2)));
        check(process(factory, input).wasSuccessful(), "wafer-limited solar recipe failed");
        check(
            state(factory).getParallelsBig()
                .equals(available),
            "solar parallel count ignored wafer availability");
        check(
            input.getBuffers()
                .get(0)
                .getItemAmountBig()
                .equals(BigInteger.valueOf(4)),
            "solar wafer remainder was lost");
    }

    private static void verifyCapacity() {
        Factory factory = new Factory(recipe(true, 3), 3);
        energy(factory, true, BigInteger.valueOf(100));
        MTEHatchOutputBus bus = new MTEHatchOutputBus("apeiron.verify.solar.output", 1, 1, new String[0], null);
        bus.setBaseMetaTileEntity(new BaseMetaTileEntity());
        factory.mOutputBusses.add(bus);
        // Keep the fluid receiver exact; only the single-slot item bus constrains the bonus.
        MTEInfiniteMEOutputAssembly fluids = output(factory);
        factory.mOutputBusses.remove(fluids);
        MTEInfinitePatternInputAssembly input = input(factory, BigInteger.valueOf(200), 2, BigInteger.valueOf(300));
        check(process(factory, input).wasSuccessful(), "finite-output solar recipe failed");
        check(
            state(factory).getParallelsBig()
                .equals(BigInteger.valueOf(14)) && itemOutputs(state(factory)).equals(BigInteger.valueOf(63)),
            "solar preflight did not reserve the 50% wafer bonus in its finite output slot");
    }

    private static void verifyRejection(boolean receiver, boolean funded, int requiredTier, BigInteger wafers) {
        Factory factory = new Factory(recipe(true, requiredTier), 2);
        UUID owner = energy(factory, true, HUGE);
        if (receiver) output(factory);
        if (!funded) WirelessNetworkManager.setUserEU(owner, BigInteger.ONE);
        MTEInfinitePatternInputAssembly input = input(factory, HUGE.multiply(BigInteger.valueOf(2)), 2, wafers);
        BigInteger before = WirelessNetworkManager.getUserEU(owner);
        BigInteger inputs = input.getBuffers()
            .get(0)
            .getItemAmountBig();
        check(!process(factory, input).wasSuccessful(), "solar prerequisite/capacity/budget bypassed");
        check(
            !state(factory).isRunning() && input.getBuffers()
                .get(0)
                .getItemAmountBig()
                .equals(inputs)
                && WirelessNetworkManager.getUserEU(owner)
                    .equals(before),
            "rejected solar recipe spent resources");
    }

    public static void verify() {
        HashMap<UUID, BigInteger> oldEnergy = GlobalVariableStorage.GlobalEnergy;
        Map<UUID, UUID> oldTeams = SpaceProjectManager.spaceTeams;
        GlobalEnergyWorldSavedData oldSave = GlobalEnergyWorldSavedData.INSTANCE;
        try {
            GlobalVariableStorage.GlobalEnergy = new HashMap<>();
            SpaceProjectManager.spaceTeams = new HashMap<>();
            GlobalEnergyWorldSavedData.INSTANCE = new GlobalEnergyWorldSavedData();
            for (boolean ultimate : new boolean[] { false, true }) {
                verifyControls(ultimate, 1, 3, true);
                verifyControls(ultimate, 2, 1, true);
                verifyControls(ultimate, 2, 2, true);
                verifyControls(ultimate, 2, 7, true);
                verifyControls(ultimate, 3, 2, true);
                verifyControls(ultimate, 3, 7, true);
                verifyControls(ultimate, 3, 1, false);
            }
            verifyNative();
            verifyWaferLimit();
            verifyCapacity();
            verifyRejection(true, true, 3, HUGE.multiply(BigInteger.valueOf(3)));
            verifyRejection(true, true, 2, BigInteger.ZERO);
            verifyRejection(true, true, 2, BigInteger.valueOf(2));
            verifyRejection(false, true, 2, HUGE.multiply(BigInteger.valueOf(3)));
            verifyRejection(true, false, 2, HUGE.multiply(BigInteger.valueOf(3)));
            Apeiron.LOG.info(
                "Solar factory verification passed: native wafer gates, exact wafer consumption, "
                    + "capped post-parallel bonuses, capacity, ordinary/ultimate controls, input/output/EU conservation and reload");
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
