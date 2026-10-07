package com.silvia.apeiron.common.machine.tst.verification;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.Nxer.TwistSpaceTechnology.common.api.random.RandomPackageFactory;
import com.Nxer.TwistSpaceTechnology.common.machine.MiscHelper;
import com.Nxer.TwistSpaceTechnology.common.machine.TST_NetherInterface;
import com.Nxer.TwistSpaceTechnology.common.machine.TST_SuperWaterPurifier;
import com.Nxer.TwistSpaceTechnology.common.material.MaterialPool;
import com.Nxer.TwistSpaceTechnology.config.Config;
import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.me.output.verification.InfiniteMEOutputAssemblySmoke;
import com.silvia.apeiron.common.machine.parallel.GeneratedRecipes;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;
import com.silvia.apeiron.mixin.gregtech.output.MultiBlockProcessingAccessor;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import gregtech.api.enums.Materials;
import gregtech.api.enums.TierEU;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;

/** Real transformed fixed-cycle generators: exact quantities, native rolls, energy and startup dispatch. */
public final class TstGeneratedEnergySmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(40)
        .add(BigInteger.valueOf(17));

    private TstGeneratedEnergySmoke() {}

    private static final class Nether extends TST_NetherInterface {

        final ArrayList<FluidStack> fluids = new ArrayList<>();

        Nether() {
            super("apeiron.verify.nether_energy");
            setBaseMetaTileEntity(new BaseMetaTileEntity());
            maxParallel = Config.MaxParallel_NetherInterface;
            powerPanelMaxParallel = maxParallel;
        }

        @Override
        public ArrayList<FluidStack> getStoredFluids() {
            ArrayList<FluidStack> result = new ArrayList<>(super.getStoredFluids());
            result.addAll(fluids);
            return result;
        }

        @Override
        public long getMaxInputEu() {
            return Config.BasicEnergyCost_NetherInterface * 3L;
        }

        @Override
        protected void sendStartMultiBlockSoundLoop() {}

        void complete() {
            super.outputAfterRecipe();
        }
    }

    private static final class Purifier extends TST_SuperWaterPurifier {

        Purifier() {
            super("apeiron.verify.purifier_energy");
            setBaseMetaTileEntity(new BaseMetaTileEntity());
        }

        @Override
        protected void sendStartMultiBlockSoundLoop() {}

        void complete() {
            super.outputAfterRecipe();
        }
    }

    public static void verify() {
        HashMap<UUID, BigInteger> energy = GlobalVariableStorage.GlobalEnergy;
        Map<UUID, UUID> teams = SpaceProjectManager.spaceTeams;
        GlobalEnergyWorldSavedData save = GlobalEnergyWorldSavedData.INSTANCE;
        RandomPackageFactory<ItemStack> items = TST_NetherInterface.ItemRandomGetter;
        FluidStack water = MiscHelper.distilledWater;
        int chance = Config.OutputHellishMetalPercent_NetherInterface;
        try {
            GlobalVariableStorage.GlobalEnergy = new HashMap<>();
            SpaceProjectManager.spaceTeams = new HashMap<>();
            GlobalEnergyWorldSavedData.INSTANCE = new GlobalEnergyWorldSavedData();
            TST_NetherInterface.ItemRandomGetter = RandomPackageFactory.<ItemStack>builder()
                .add(new ItemStack(Items.diamond, 64), 1)
                .build(new ItemStack[0]);
            MiscHelper.distilledWater = FluidRegistry.getFluidStack("ic2distilledwater", 1);
            Config.OutputHellishMetalPercent_NetherInterface = 100;
            // TST initializes its pool at load-complete, after this detached post-init self-check.
            Field pool = TST_SuperWaterPurifier.class.getDeclaredField("fluidRandomGetter");
            Field uum = TST_SuperWaterPurifier.class.getDeclaredField("UUM");
            Field uumc = TST_SuperWaterPurifier.class.getDeclaredField("UUMC");
            pool.setAccessible(true);
            uum.setAccessible(true);
            uumc.setAccessible(true);
            Object oldPool = pool.get(null), oldUum = uum.get(null), oldUumc = uumc.get(null);
            try {
                TST_SuperWaterPurifier.initStatics();
                verifyNative();
                verifyNether();
                verifySettings();
                verifyStocking();
                verifyBudget();
                verifyOutputCapacity();
                verifyPurifier(false);
                verifyPurifier(true);
            } finally {
                pool.set(null, oldPool);
                uum.set(null, oldUum);
                uumc.set(null, oldUumc);
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("TST generated-energy fixture setup", error);
        } finally {
            GlobalVariableStorage.GlobalEnergy = energy;
            SpaceProjectManager.spaceTeams = teams;
            GlobalEnergyWorldSavedData.INSTANCE = save;
            TST_NetherInterface.ItemRandomGetter = items;
            MiscHelper.distilledWater = water;
            Config.OutputHellishMetalPercent_NetherInterface = chance;
        }
        Apeiron.LOG.info(
            "TST generated-energy verification passed: Nether Interface / Super Water Purifier, "
                + "native fallback, ordinary voltage, ultimate time, exact input/output, fixed upkeep, budget, pause/reload");
    }

    private static ApeironMachineTile tile(int offset) {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(offset));
        return tile;
    }

    private static UUID energy(MTEMultiBlockBase machine, boolean ultimate) {
        UUID owner = UUID.randomUUID();
        ApeironMachineTile tile = tile(
            ultimate ? ApeironMachines.ULTIMATE_ENERGY_HATCH_OFFSET : ApeironMachines.INFINITE_ENERGY_HATCH_OFFSET);
        tile.setOwnerUuid(owner);
        check(machine.addToMachineList(tile, 0), "native structure adder rejected infinite energy hatch");
        machine.mWrench = machine.mScrewdriver = machine.mSoftMallet = machine.mHardHammer = machine.mSolderingTool = machine.mCrowbar = true;
        state(machine).setParallelSettingBig(BigInteger.ZERO);
        state(machine).setTargetDuration(7);
        return owner;
    }

    private static MTEInfiniteMEOutputAssembly output(MTEMultiBlockBase machine) {
        MTEInfiniteMEOutputAssembly output = InfiniteMEOutputAssemblySmoke.assembly();
        output.getProvider()
            .setCheckMode(true);
        check(machine.addToMachineList(output.getBaseMetaTileEntity(), 0), "native output structure adder failed");
        return output;
    }

    private static MTEInfinitePatternInputAssembly input(MTEMultiBlockBase machine, FluidStack type,
        BigInteger amount) {
        MTEInfinitePatternInputAssembly input = (MTEInfinitePatternInputAssembly) tile(
            ApeironMachines.PATTERN_INPUT_ASSEMBLY_OFFSET).getMetaTileEntity();
        check(
            input.addToBufferBig(
                0,
                Collections.singletonList(BigAEStackValues.copyWithSize(AEFluidStack.create(type), amount))),
            "fluid fixture setup");
        machine.mDualInputHatches.add(input);
        return input;
    }

    private static WirelessRecipeState state(MTEMultiBlockBase machine) {
        return ((BigWirelessController) machine).getWirelessRecipeState();
    }

    private static boolean start(MTEMultiBlockBase machine, MTEInfinitePatternInputAssembly input) {
        input.beginRecipeProcessing();
        try {
            return ((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe();
        } finally {
            input.endRecipeProcessing();
        }
    }

    private static BigInteger cost(BigInteger parallel) {
        return parallel.add(BigInteger.valueOf(2))
            .multiply(BigInteger.valueOf(Config.BasicEnergyCost_NetherInterface))
            .multiply(BigInteger.valueOf(Config.CycleTime_NetherInterface));
    }

    private static void verifyNative() {
        Nether machine = new Nether();
        FluidStack water = MiscHelper.distilledWater.copy();
        water.amount = Config.BasicDistilledWaterCost_NetherInterface;
        machine.fluids.add(water);
        state(machine).setParallelSettingBig(BigInteger.TEN);
        state(machine).setTargetDuration(1);
        check(GeneratedRecipes.process(machine) == null, "native machine entered infinite-energy execution");
        check(((MultiBlockProcessingAccessor) (Object) machine).apeiron$checkRecipe(), "native nether recipe failed");
        check(
            !state(machine).isRunning() && water.amount == 0
                && machine.mMaxProgresstime == Config.CycleTime_NetherInterface
                && machine.lEUt == -Config.BasicEnergyCost_NetherInterface * 3L,
            "native cycle or energy changed");
        check(machine.mOutputItems[0].stackSize == 64, "native random outputs changed");
        check(GeneratedRecipes.process(new Purifier()) == null, "native purifier entered exact transaction");
    }

    private static void verifyNether() {
        Nether machine = new Nether();
        UUID owner = energy(machine, true);
        MTEInfiniteMEOutputAssembly output = output(machine);
        MTEInfinitePatternInputAssembly input = input(
            machine,
            MiscHelper.distilledWater,
            HUGE.multiply(BigInteger.valueOf(Config.BasicDistilledWaterCost_NetherInterface)));
        WirelessNetworkManager.setUserEU(owner, cost(HUGE));
        check(start(machine, input), "ultimate nether recipe rejected");
        check(
            state(machine).getParallelsBig()
                .equals(HUGE) && machine.mMaxProgresstime == 7,
            "nether retained native parallel or time limits");
        check(
            input.getBuffers()
                .get(0)
                .getFluidAmountBig()
                .signum() == 0,
            "distilled water debit truncated");
        check(
            state(machine).getTotalEUBig()
                .equals(cost(HUGE)),
            "fixed teleporter upkeep lost or multiplied");
        check(
            WirelessNetworkManager.getUserEU(owner)
                .equals(cost(HUGE)),
            "startup energy charged twice");
        check(machine.mOutputItems == null && machine.mOutputFluids == null, "int output arrays generated");
        check(machine.onRunningTick(null), "first nether wireless tick failed");
        BigInteger remaining = WirelessNetworkManager.getUserEU(owner);
        BigInteger next = state(machine).nextDebit(10000);
        WirelessNetworkManager.setUserEU(owner, BigInteger.ZERO);
        check(!machine.onRunningTick(null) && next.equals(state(machine).nextDebit(10000)), "pause lost progress");
        NBTTagCompound saved = new NBTTagCompound();
        machine.saveNBTData(saved);
        Nether restored = new Nether();
        restored.loadNBTData(saved);
        check(
            state(restored).nextDebit(10000)
                .equals(next)
                && state(restored).getParallelsBig()
                    .equals(HUGE),
            "large generator state failed to reload");
        WirelessNetworkManager.setUserEU(owner, remaining);
        for (int i = 1; i < 7; i++) check(machine.onRunningTick(null), "resumed debit failed");
        check(
            WirelessNetworkManager.getUserEU(owner)
                .signum() == 0,
            "nether cycle energy not conserved");
        BigInteger itemTotal = total(state(machine).getOutputDisplay(), false);
        BigInteger fluidTotal = total(state(machine).getOutputDisplay(), true);
        check(
            itemTotal
                .equals(HUGE.multiply(BigInteger.valueOf(64L * Config.GenerateStackEveryProcessing_NetherInterface)))
                && fluidTotal
                    .equals(HUGE.multiply(BigInteger.valueOf(Config.BasicDistilledWaterCost_NetherInterface + 288L))),
            "big random outputs lost quantities");
        machine.complete();
        machine.complete();
        check(
            output.getProvider()
                .getCachedAmountBig()
                .equals(itemTotal)
                && output.getFluidProvider()
                    .getCachedAmountBig()
                    .equals(fluidTotal),
            "completion duplicated or truncated");
    }

    private static void verifyBudget() {
        for (boolean ultimate : new boolean[] { false, true }) {
            Nether machine = new Nether();
            UUID owner = energy(machine, ultimate);
            output(machine);
            state(machine).setParallelSettingBig(BigInteger.valueOf(5));
            state(machine).setVoltageSetting(Config.BasicEnergyCost_NetherInterface * 7L);
            MTEInfinitePatternInputAssembly input = input(
                machine,
                MiscHelper.distilledWater,
                BigInteger.valueOf(Config.BasicDistilledWaterCost_NetherInterface * 10L));
            WirelessNetworkManager.setUserEU(owner, cost(BigInteger.valueOf(3)));
            check(start(machine, input), "limited generator failed");
            check(
                state(machine).getParallelsBig()
                    .equals(BigInteger.valueOf(3)),
                "whole-batch budget ignored upkeep");
            check(
                machine.mMaxProgresstime == (ultimate ? 7 : Config.CycleTime_NetherInterface),
                "ordinary cycle changed");
            check(
                input.getBuffers()
                    .get(0)
                    .getFluidAmountBig()
                    .equals(BigInteger.valueOf(Config.BasicDistilledWaterCost_NetherInterface * 7L)),
                "budget over-consumed water");
        }
        Nether voltage = new Nether();
        UUID owner = energy(voltage, false);
        output(voltage);
        state(voltage).setVoltageSetting(Config.BasicEnergyCost_NetherInterface * 4L);
        MTEInfinitePatternInputAssembly input = input(
            voltage,
            MiscHelper.distilledWater,
            BigInteger.valueOf(Config.BasicDistilledWaterCost_NetherInterface * 10L));
        WirelessNetworkManager.setUserEU(owner, cost(BigInteger.valueOf(10)));
        check(
            start(voltage, input) && state(voltage).getParallelsBig()
                .equals(BigInteger.valueOf(2)),
            "ordinary voltage setting ignored");
        Nether empty = new Nether();
        UUID emptyOwner = energy(empty, true);
        output(empty);
        MTEInfinitePatternInputAssembly emptyInput = input(
            empty,
            MiscHelper.distilledWater,
            BigInteger.valueOf(Config.BasicDistilledWaterCost_NetherInterface));
        WirelessNetworkManager.setUserEU(emptyOwner, cost(BigInteger.ONE).subtract(BigInteger.ONE));
        check(
            !start(empty, emptyInput) && emptyInput.getBuffers()
                .get(0)
                .getFluidAmountBig()
                .equals(BigInteger.valueOf(Config.BasicDistilledWaterCost_NetherInterface)),
            "failed startup consumed inputs");
    }

    private static void verifySettings() {
        for (int duration : new int[] { 1, 128 }) {
            Nether machine = new Nether();
            check(
                state(machine).getTargetDuration() == 128 && state(machine).getParallelSettingBig()
                    .equals(BigInteger.valueOf(Integer.MAX_VALUE)),
                "generator defaults changed");
            UUID owner = energy(machine, true);
            output(machine);
            state(machine).setTargetDuration(duration);
            state(machine).setParallelSettingBig(BigInteger.valueOf(5));
            state(machine).setVoltageSetting(1);
            MTEInfinitePatternInputAssembly input = input(
                machine,
                MiscHelper.distilledWater,
                BigInteger.valueOf(Config.BasicDistilledWaterCost_NetherInterface * 10L));
            WirelessNetworkManager.setUserEU(owner, cost(BigInteger.TEN));
            check(
                start(machine, input) && machine.mMaxProgresstime == duration
                    && state(machine).getParallelsBig()
                        .equals(BigInteger.valueOf(5)),
                "ultimate ignored manual parallel/time or used ordinary voltage setting");
            for (int tick = 0; tick < duration; tick++)
                check(machine.onRunningTick(null), "configured generator debit failed");
            check(
                WirelessNetworkManager.getUserEU(owner)
                    .equals(cost(BigInteger.TEN).subtract(cost(BigInteger.valueOf(5)))),
                "completion time changed base energy");
        }
    }

    private static void verifyStocking() {
        for (boolean mixed : new boolean[] { false, true }) {
            Nether machine = new Nether();
            UUID owner = energy(machine, true);
            output(machine);
            WirelessNetworkManager.setUserEU(owner, cost(HUGE));
            FluidStack water = MiscHelper.distilledWater.copy();
            water.amount = Config.BasicDistilledWaterCost_NetherInterface;
            com.silvia.apeiron.common.machine.me.stocking.verification.StockingInputsSmoke
                .verifyGeneratedInput(machine, water, HUGE, mixed);
        }
    }

    private static void verifyOutputCapacity() {
        Nether machine = new Nether();
        UUID owner = energy(machine, true);
        output(machine);
        machine.mOutputHatches.clear();
        MTEHatchOutput physical = new MTEHatchOutput("apeiron.verify.nether_tank", 1, new String[0], null);
        physical.setBaseMetaTileEntity(new BaseMetaTileEntity());
        machine.mOutputHatches.add(physical);
        MTEInfinitePatternInputAssembly input = input(
            machine,
            MiscHelper.distilledWater,
            BigInteger.valueOf(Config.BasicDistilledWaterCost_NetherInterface * 10L));
        WirelessNetworkManager.setUserEU(owner, cost(BigInteger.TEN));
        // One single-fluid tank cannot accept both waste and molten hellish metal.
        check(
            !start(machine, input) && !state(machine).isRunning()
                && input.getBuffers()
                    .get(0)
                    .getFluidAmountBig()
                    .equals(BigInteger.valueOf(Config.BasicDistilledWaterCost_NetherInterface * 10L)),
            "output preflight lost inputs or accepted incompatible fluid types");
        check(physical.getFillableStack() == null, "output simulation committed a mutation");
        check(
            machine.getCheckRecipeResult()
                .equals(gregtech.api.recipe.check.CheckRecipeResultRegistry.FLUID_OUTPUT_FULL),
            "generator fluid capacity failure reported the wrong channel");

        Nether missingItems = new Nether();
        UUID itemOwner = energy(missingItems, true);
        output(missingItems);
        missingItems.mOutputBusses.clear();
        MTEInfinitePatternInputAssembly itemInput = input(
            missingItems,
            MiscHelper.distilledWater,
            BigInteger.valueOf(Config.BasicDistilledWaterCost_NetherInterface));
        WirelessNetworkManager.setUserEU(itemOwner, cost(BigInteger.ONE));
        check(
            !start(missingItems, itemInput) && missingItems.getCheckRecipeResult()
                .equals(gregtech.api.recipe.check.CheckRecipeResultRegistry.ITEM_OUTPUT_FULL),
            "generator item capacity failure reported fluid output full");
        check(
            itemInput.getBuffers()
                .get(0)
                .getFluidAmountBig()
                .equals(BigInteger.valueOf(Config.BasicDistilledWaterCost_NetherInterface)),
            "generator missing item output consumed water");
    }

    private static void verifyPurifier(boolean concentrated) {
        Purifier machine = new Purifier();
        UUID owner = energy(machine, true);
        MTEInfiniteMEOutputAssembly output = output(machine);
        FluidStack fluid = concentrated ? MaterialPool.ConcentratedUUMatter.getFluidOrGas(1)
            : Materials.UUMatter.getFluid(1000);
        MTEInfinitePatternInputAssembly input = input(machine, fluid, HUGE.multiply(BigInteger.valueOf(fluid.amount)));
        BigInteger total = HUGE.multiply(BigInteger.valueOf(TierEU.RECIPE_UMV))
            .multiply(BigInteger.valueOf(1200));
        WirelessNetworkManager.setUserEU(owner, total);
        check(start(machine, input), "ultimate purifier recipe rejected");
        check(
            state(machine).getParallelsBig()
                .equals(HUGE) && machine.mMaxProgresstime == 7
                && state(machine).getTotalEUBig()
                    .equals(total),
            "purifier limits or base energy changed");
        check(
            input.getBuffers()
                .get(0)
                .getFluidAmountBig()
                .signum() == 0,
            "purifier consumed wrong UU amount");
        BigInteger expected = HUGE.multiply(BigInteger.valueOf(concentrated ? 6000 : 3000));
        check(total(state(machine).getOutputDisplay(), true).equals(expected), "purifier random yields truncated");
        for (int i = 0; i < 7; i++) check(machine.onRunningTick(null), "purifier wireless tick failed");
        check(
            WirelessNetworkManager.getUserEU(owner)
                .signum() == 0,
            "purifier total energy not conserved");
        machine.complete();
        check(
            output.getFluidProvider()
                .getCachedAmountBig()
                .equals(expected),
            "purifier completion lost outputs");
    }

    private static BigInteger total(List<IAEStack<?>> outputs, boolean fluids) {
        BigInteger result = BigInteger.ZERO;
        for (IAEStack<?> stack : outputs) if (fluids ? stack instanceof IAEFluidStack : stack instanceof IAEItemStack)
            result = result.add(BigAEStackValues.get(stack));
        return result;
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new IllegalStateException("TST generated energy: " + message);
    }
}
