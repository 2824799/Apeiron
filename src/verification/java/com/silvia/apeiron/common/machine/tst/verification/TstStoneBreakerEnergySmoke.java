package com.silvia.apeiron.common.machine.tst.verification;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import com.Nxer.TwistSpaceTechnology.common.machine.TST_MegaStoneBreaker;
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
import gregtech.api.enums.Materials;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTUtility;
import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;

/** Real TST controller and its custom parallel callback around the shared exact recipe transaction. */
public final class TstStoneBreakerEnergySmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(40)
        .add(BigInteger.valueOf(17));
    private static int mapId;

    private TstStoneBreakerEnergySmoke() {}

    private static final class Machine extends TST_MegaStoneBreaker {

        private final RecipeMap<?> recipes;

        Machine() {
            this(false);
        }

        Machine(boolean generator) {
            super("apeiron.verify.stone_breaker");
            setBaseMetaTileEntity(new BaseMetaTileEntity());
            mWrench = mScrewdriver = mSoftMallet = mHardHammer = mSolderingTool = mCrowbar = true;
            recipes = RecipeMapBuilder.of("apeiron.verify.stone_breaker." + mapId++)
                .maxIO(1, 1, 0, 0)
                .build();
            recipes.addRecipe(
                GTRecipeBuilder.builder()
                    .itemInputs(
                        generator ? GTUtility.copyAmountUnsafe(0, GTUtility.getIntegratedCircuit(1))
                            : new ItemStack(Items.diamond, 2))
                    .itemOutputs(new ItemStack(Blocks.cobblestone, 3))
                    .duration(20)
                    .eut(6)
                    .build()
                    .get());
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
                .setAvailableVoltage(6)
                .setAvailableAmperage(1)
                .setVoidProtection(false, false);
            check(
                logic.process()
                    .wasSuccessful(),
                "native recipe failed");
            return logic;
        }
    }

    private static ApeironMachineTile tile(int offset) {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(offset));
        return tile;
    }

    private static WirelessRecipeState state(Machine machine) {
        return ((BigWirelessController) (Object) machine).getWirelessRecipeState();
    }

    public static void verify() {
        HashMap<UUID, BigInteger> energy = GlobalVariableStorage.GlobalEnergy;
        Map<UUID, UUID> teams = SpaceProjectManager.spaceTeams;
        GlobalEnergyWorldSavedData save = GlobalEnergyWorldSavedData.INSTANCE;
        try {
            GlobalVariableStorage.GlobalEnergy = new HashMap<>();
            SpaceProjectManager.spaceTeams = new HashMap<>();
            GlobalEnergyWorldSavedData.INSTANCE = new GlobalEnergyWorldSavedData();
            verifyNative();
            for (boolean ultimate : new boolean[] { false, true })
                for (boolean boosted : new boolean[] { false, true }) verifyControls(ultimate, boosted);
            verifyVoltage();
            verifyBudget();
            verifyGenerator();
            verifyCapacity();
            verifyReload();
        } finally {
            GlobalVariableStorage.GlobalEnergy = energy;
            SpaceProjectManager.spaceTeams = teams;
            GlobalEnergyWorldSavedData.INSTANCE = save;
        }
        Apeiron.LOG.info(
            "TST stone breaker energy verification passed: native fallback, ordinary/ultimate controls, "
                + "4x/1024x output, exact inputs/EU, catalysts, budget/capacity, periodic fluids and reload");
    }

    private static UUID energy(Machine machine, boolean ultimate, BigInteger parallel, BigInteger budget) {
        ApeironMachineTile energy = tile(
            ultimate ? ApeironMachines.ULTIMATE_ENERGY_HATCH_OFFSET : ApeironMachines.INFINITE_ENERGY_HATCH_OFFSET);
        UUID owner = UUID.randomUUID();
        energy.setOwnerUuid(owner);
        check(machine.addToMachineList(energy, 0), "energy registration");
        state(machine).setParallelSettingBig(parallel);
        state(machine).setVoltageSetting(6);
        state(machine).setTargetDuration(7);
        WirelessNetworkManager.setUserEU(owner, budget);
        return owner;
    }

    private static MTEInfiniteMEOutputAssembly output(Machine machine) {
        MTEInfiniteMEOutputAssembly output = InfiniteMEOutputAssemblySmoke.assembly();
        output.getProvider()
            .setCheckMode(true);
        check(machine.addToMachineList(output.getBaseMetaTileEntity(), 0), "output registration");
        return output;
    }

    private static MTEInfinitePatternInputAssembly input(Machine machine, ItemStack type, BigInteger amount) {
        MTEInfinitePatternInputAssembly input = (MTEInfinitePatternInputAssembly) tile(
            ApeironMachines.PATTERN_INPUT_ASSEMBLY_OFFSET).getMetaTileEntity();
        check(
            input.addToBufferBig(
                0,
                Collections.singletonList(BigAEStackValues.copyWithSize(AEItemStack.create(type), amount))),
            "seed inputs");
        machine.mDualInputHatches.add(input);
        return input;
    }

    private static MTEHatchInput[] boost(Machine machine, int amount) {
        MTEHatchInput water = new MTEHatchInput("apeiron.verify.stone_water", 5, new String[0], null);
        MTEHatchInput lava = new MTEHatchInput("apeiron.verify.stone_lava", 5, new String[0], null);
        water.setBaseMetaTileEntity(new BaseMetaTileEntity());
        lava.setBaseMetaTileEntity(new BaseMetaTileEntity());
        water.mFluid = new FluidStack(Materials.Water.mFluid, amount);
        lava.mFluid = new FluidStack(Materials.Lava.mFluid, amount);
        check(machine.addWaterHatch(water.getBaseMetaTileEntity(), (short) 0), "water hatch registration");
        check(machine.addLavaHatch(lava.getBaseMetaTileEntity(), (short) 0), "lava hatch registration");
        return new MTEHatchInput[] { water, lava };
    }

    private static CheckRecipeResult process(Machine machine, MTEInfinitePatternInputAssembly input) {
        input.beginRecipeProcessing();
        try {
            return machine.checkProcessing();
        } finally {
            input.endRecipeProcessing();
        }
    }

    private static BigInteger produced(Machine machine) {
        return state(machine).getOutputDisplay()
            .stream()
            .map(BigAEStackValues::get)
            .reduce(BigInteger.ZERO, BigInteger::add);
    }

    private static void verifyNative() {
        Machine machine = new Machine();
        state(machine).setParallelSettingBig(HUGE);
        state(machine).setTargetDuration(1);
        ItemStack input = new ItemStack(Items.diamond, 2);
        ProcessingLogic logic = machine.nativeProcess(input);
        check(
            !state(machine).isRunning() && !state(machine).hasPreparedRecipe(),
            "native machine entered exact execution");
        check(
            logic.getDuration() == 20 && logic.getCurrentParallels() == 1 && input.stackSize == 0,
            "native duration, parallel or consumption changed");
        check(logic.getOutputItems()[0].stackSize == 12, "native 4x output changed");
        NBTTagCompound saved = new NBTTagCompound();
        machine.saveNBTData(saved);
        check(!saved.hasKey("ApeironStoneBreakerBoost"), "idle/native boost state persisted");
    }

    private static void verifyControls(boolean ultimate, boolean boosted) {
        Machine machine = new Machine();
        BigInteger cost = HUGE.multiply(BigInteger.valueOf(120));
        UUID owner = energy(machine, ultimate, HUGE, cost);
        MTEInfiniteMEOutputAssembly output = output(machine);
        MTEInfinitePatternInputAssembly input = input(
            machine,
            new ItemStack(Items.diamond),
            HUGE.multiply(BigInteger.valueOf(2)));
        MTEHatchInput[] fluids = boosted ? boost(machine, 3000) : null;
        CheckRecipeResult result = process(machine, input);
        check(result.wasSuccessful(), "recipe failed: " + result.getID());
        check(state(machine).isRunning(), "custom output callback bypassed exact energy processing");
        check(
            state(machine).getParallelsBig()
                .equals(HUGE) && machine.mMaxProgresstime == (ultimate ? 7 : 20),
            "parallel or target duration ignored");
        check(
            state(machine).getTotalEUBig()
                .equals(cost)
                && WirelessNetworkManager.getUserEU(owner)
                    .equals(cost),
            "recipe energy changed or was prepaid");
        check(
            input.getBuffers()
                .get(0)
                .isEmpty(),
            "exact input not consumed");
        BigInteger expected = HUGE.multiply(BigInteger.valueOf(3L * (boosted ? 1024 : 4)));
        check(produced(machine).equals(expected), "custom output multiplier lost");
        if (boosted) check(
            fluids[0].mFluid.amount == 3000 && fluids[1].mFluid.amount == 3000,
            "capacity/start simulation consumed fluids");
        for (int i = 0; i < machine.mMaxProgresstime; i++) check(machine.onRunningTick(null), "wireless debit failed");
        check(
            WirelessNetworkManager.getUserEU(owner)
                .signum() == 0,
            "total energy not conserved");
        if (boosted) check(
            fluids[0].mFluid.amount == 2000 && fluids[1].mFluid.amount == 2000,
            "native periodic fluid consumption changed");
        machine.complete();
        machine.complete();
        check(
            output.getProvider()
                .getCachedAmountBig()
                .equals(expected),
            "completion truncated or duplicated output");
        NBTTagCompound saved = new NBTTagCompound();
        machine.saveNBTData(saved);
        check(!saved.hasKey("ApeironStoneBreakerBoost"), "completed boost state persisted");
    }

    private static void verifyBudget() {
        Machine machine = new Machine();
        UUID owner = energy(machine, true, BigInteger.valueOf(20), BigInteger.valueOf(360));
        output(machine);
        MTEInfinitePatternInputAssembly input = input(machine, new ItemStack(Items.diamond), BigInteger.valueOf(20));
        boost(machine, 999);
        check(process(machine, input).wasSuccessful(), "budget-limited recipe failed");
        check(
            state(machine).getParallelsBig()
                .equals(BigInteger.valueOf(3))
                && input.getBuffers()
                    .get(0)
                    .getItemAmountBig()
                    .equals(BigInteger.valueOf(14))
                && produced(machine).equals(BigInteger.valueOf(36)),
            "budget, inputs or sub-bucket boost ignored");
        BigInteger next = state(machine).nextDebit(10000);
        WirelessNetworkManager.setUserEU(owner, BigInteger.ZERO);
        check(
            !machine.onRunningTick(null) && state(machine).nextDebit(10000)
                .equals(next),
            "pause advanced paid progress");
        WirelessNetworkManager.setUserEU(owner, BigInteger.valueOf(360));
        for (int i = 0; i < 7; i++) check(machine.onRunningTick(null), "resume failed");
        check(
            WirelessNetworkManager.getUserEU(owner)
                .signum() == 0,
            "budget debit changed");
    }

    private static void verifyVoltage() {
        Machine machine = new Machine();
        UUID owner = energy(machine, false, BigInteger.valueOf(3), BigInteger.valueOf(720));
        state(machine).setVoltageSetting(128);
        output(machine);
        MTEInfinitePatternInputAssembly input = input(machine, new ItemStack(Items.diamond), BigInteger.valueOf(6));
        check(process(machine, input).wasSuccessful(), "ordinary overclock failed");
        check(
            machine.mMaxProgresstime == 10 && state(machine).getParallelsBig()
                .equals(BigInteger.valueOf(3))
                && state(machine).getTotalEUBig()
                    .equals(BigInteger.valueOf(720)),
            "ordinary voltage/overclock ignored");
        for (int i = 0; i < 10; i++) check(machine.onRunningTick(null), "ordinary overclock debit failed");
        check(
            WirelessNetworkManager.getUserEU(owner)
                .signum() == 0,
            "ordinary overclock total energy changed");
    }

    private static void verifyGenerator() {
        Machine machine = new Machine(true);
        energy(machine, true, BigInteger.ZERO, BigInteger.valueOf(360));
        output(machine);
        MTEInfinitePatternInputAssembly input = input(machine, GTUtility.getIntegratedCircuit(1), BigInteger.ONE);
        check(process(machine, input).wasSuccessful(), "catalyst-only generator failed");
        check(
            state(machine).getParallelsBig()
                .equals(BigInteger.valueOf(3))
                && input.getBuffers()
                    .get(0)
                    .getItemAmountBig()
                    .equals(BigInteger.ONE),
            "generator ignored finite budget or consumed catalyst");
    }

    private static void verifyCapacity() {
        Machine machine = new Machine();
        UUID owner = energy(machine, true, BigInteger.ONE, BigInteger.valueOf(120));
        MTEHatchOutputBus physical = new MTEHatchOutputBus("apeiron.verify.stone_output", 0, new String[0], null);
        physical.setBaseMetaTileEntity(new BaseMetaTileEntity());
        check(machine.addToMachineList(physical.getBaseMetaTileEntity(), 0), "physical output registration");
        MTEInfinitePatternInputAssembly input = input(machine, new ItemStack(Items.diamond), BigInteger.valueOf(2));
        MTEHatchInput[] fluids = boost(machine, 1000);
        CheckRecipeResult result = process(machine, input);
        check(
            !result.wasSuccessful() && !state(machine).isRunning(),
            "1024x output bypassed capacity before native start");
        check(
            input.getBuffers()
                .get(0)
                .getItemAmountBig()
                .equals(BigInteger.valueOf(2))
                && WirelessNetworkManager.getUserEU(owner)
                    .equals(BigInteger.valueOf(120))
                && fluids[0].mFluid.amount == 1000
                && fluids[1].mFluid.amount == 1000,
            "rejected capacity consumed resources");
        machine.mOutputBusses.clear();
        output(machine);
        check(
            process(machine, input).wasSuccessful() && produced(machine).equals(BigInteger.valueOf(3072)),
            "capacity recovery lost boost");
    }

    private static void verifyReload() {
        Machine machine = new Machine();
        UUID owner = energy(machine, true, BigInteger.valueOf(3), BigInteger.valueOf(360));
        state(machine).setTargetDuration(25);
        output(machine);
        MTEInfinitePatternInputAssembly input = input(machine, new ItemStack(Items.diamond), BigInteger.valueOf(6));
        boost(machine, 3000);
        check(process(machine, input).wasSuccessful() && machine.onRunningTick(null), "reload setup failed");
        NBTTagCompound saved = new NBTTagCompound();
        machine.saveNBTData(saved);
        Machine restored = new Machine();
        restored.mEnergyHatches.addAll(machine.mEnergyHatches);
        MTEInfiniteMEOutputAssembly output = output(restored);
        MTEHatchInput[] fluids = boost(restored, 2000);
        restored.loadNBTData(saved);
        check(
            state(restored).getParallelsBig()
                .equals(BigInteger.valueOf(3)) && produced(restored).equals(BigInteger.valueOf(9216)),
            "reload lost parallel/output state");
        check(
            restored.onRunningTick(null) && fluids[0].mFluid.amount == 2000 && fluids[1].mFluid.amount == 2000,
            "reload repeated the first fluid debit");
        for (int i = 2; i < 25; i++) check(restored.onRunningTick(null), "reloaded debit failed");
        check(
            fluids[0].mFluid.amount == 1000 && fluids[1].mFluid.amount == 1000,
            "reload disabled boost or lost periodic fluid debit");
        check(
            WirelessNetworkManager.getUserEU(owner)
                .signum() == 0,
            "reloaded energy not conserved");
        restored.complete();
        restored.complete();
        check(
            output.getProvider()
                .getCachedAmountBig()
                .equals(BigInteger.valueOf(9216)),
            "reloaded output lost or duplicated");
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new IllegalStateException("TST stone breaker verification: " + message);
    }
}
