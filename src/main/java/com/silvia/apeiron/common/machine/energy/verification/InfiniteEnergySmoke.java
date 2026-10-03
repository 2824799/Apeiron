package com.silvia.apeiron.common.machine.energy.verification;

import java.math.BigInteger;
import java.util.Arrays;
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
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputBus;
import com.silvia.apeiron.common.machine.parallel.NativeParallelPolicy;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;

import appeng.util.item.AEItemStack;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;
import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;
import gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace;

/** Full shared processing, exact inputs, extended running debit and persistence on detached real GT classes. */
public final class InfiniteEnergySmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(60)
        .add(BigInteger.valueOf(17));

    private InfiniteEnergySmoke() {}

    private static final class Controller extends MTEElectricBlastFurnace {

        private RecipeMap<?> recipes;

        private Controller() {
            super("apeiron.verification.wireless_controller");
            setBaseMetaTileEntity(new BaseMetaTileEntity());
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return recipes;
        }

        @Override
        protected ProcessingLogic createProcessingLogic() {
            return new ProcessingLogic() {

                @Override
                protected OverclockCalculator createOverclockCalculator(GTRecipe recipe) {
                    return OverclockCalculator.ofNoOverclock(recipe);
                }
            }.setMaxParallelSupplier(this::getTrueParallel);
        }

        private void complete() {
            outputAfterRecipe();
        }
    }

    private static ApeironMachineTile tile(int offset) {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(offset));
        return tile;
    }

    public static void verify() {
        HashMap<UUID, BigInteger> oldEnergy = GlobalVariableStorage.GlobalEnergy;
        Map<UUID, UUID> oldTeams = SpaceProjectManager.spaceTeams;
        GlobalEnergyWorldSavedData oldSave = GlobalEnergyWorldSavedData.INSTANCE;
        try {
            GlobalVariableStorage.GlobalEnergy = new HashMap<>();
            SpaceProjectManager.spaceTeams = new HashMap<>();
            GlobalEnergyWorldSavedData.INSTANCE = new GlobalEnergyWorldSavedData();
            check(NativeParallelPolicy.supports(new ParallelHelper()), "standard helper rejected");
            check(
                !NativeParallelPolicy
                    .supports(new ParallelHelper().setCustomItemOutputCalculation(count -> new ItemStack[0])),
                "custom output callback was replaced");
            check(
                NativeParallelPolicy.supports(new ParallelHelper().enableBatchMode(4)),
                "default batch flag prevented exact parallel execution");
            UUID owner = UUID.randomUUID();
            ApeironMachineTile energyTile = tile(ApeironMachines.INFINITE_ENERGY_HATCH_OFFSET);
            energyTile.setOwnerUuid(owner);
            MTEInfiniteEnergyHatch hatch = (MTEInfiniteEnergyHatch) energyTile.getMetaTileEntity();
            check(
                hatch.maxEUStore() == 0 && hatch.maxEUInput() == Integer.MAX_VALUE,
                "wireless hatch voltage or cache");
            check(energyTile.getInputAmperage() == Integer.MAX_VALUE, "wireless hatch has no advertised amperage");
            Controller controller = new Controller();
            controller.mEnergyHatches.add(hatch);
            controller.mOutputBusses.add((MTEBoundlessMEOutputBus) tile(0).getMetaTileEntity());
            controller.mWrench = controller.mScrewdriver = controller.mSoftMallet = controller.mHardHammer = controller.mSolderingTool = controller.mCrowbar = true;
            controller.recipes = RecipeMapBuilder.of("apeiron.verification.wireless_recipes")
                .maxIO(1, 1, 0, 0)
                .build();
            controller.recipes.addRecipe(
                new GTRecipe(
                    false,
                    new ItemStack[] { new ItemStack(Items.diamond, 2) },
                    new ItemStack[] { new ItemStack(Items.emerald, 3) },
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    20,
                    8,
                    0));
            MTEInfinitePatternInputAssembly source = (MTEInfinitePatternInputAssembly) tile(4).getMetaTileEntity();
            check(
                source.addToBufferBig(
                    0,
                    Arrays.asList(
                        BigAEStackValues.copyWithSize(
                            AEItemStack.create(new ItemStack(Items.diamond)),
                            HUGE.multiply(BigInteger.valueOf(2))))),
                "cannot seed exact input");
            controller.mDualInputHatches.add(source);
            WirelessRecipeState state = ((BigWirelessController) (Object) controller).getWirelessRecipeState();
            check(
                state.getParallelSettingBig()
                    .equals(BigInteger.valueOf(Integer.MAX_VALUE)),
                "unsafe default parallel setting");
            state.setParallelSettingBig(BigInteger.ZERO);
            check(
                state.getLimit()
                    .isUnlimited(),
                "manual zero does not mean unlimited");
            WirelessNetworkManager.setUserEU(owner, HUGE.multiply(BigInteger.valueOf(8 * 20L)));
            MTEBoundlessMEOutputBus receiving = (MTEBoundlessMEOutputBus) controller.mOutputBusses.get(0);
            controller.mOutputBusses.clear();
            source.beginRecipeProcessing();
            try {
                check(
                    !controller.checkProcessing()
                        .wasSuccessful(),
                    "output protection accepted a missing receiver");
            } finally {
                source.endRecipeProcessing();
            }
            check(
                source.getBuffers()
                    .get(0)
                    .getItemAmountBig()
                    .equals(HUGE.multiply(BigInteger.valueOf(2))) && !state.isRunning(),
                "rejected preflight consumed input or started a recipe");
            controller.mOutputBusses.add(receiving);
            source.beginRecipeProcessing();
            try {
                gregtech.api.recipe.check.CheckRecipeResult result = controller.checkProcessing();
                check(result.wasSuccessful(), "shared exact recipe processing failed: " + result.getID());
            } finally {
                source.endRecipeProcessing();
            }
            check(
                state.getParallelsBig()
                    .equals(HUGE),
                "parallel count truncated");
            check(
                state.getEUtBig()
                    .equals(HUGE.multiply(BigInteger.valueOf(8))),
                "wireless EU/t truncated");
            check(
                source.getBuffers()
                    .get(0)
                    .isEmpty(),
                "exact recipe inputs were not consumed");
            check(controller.mMaxProgresstime == 20, "native recipe duration changed");
            WirelessRecipeDisplaySmoke.verify(state);
            BigInteger balance = hatch.getAvailableEUBig();
            check(controller.onRunningTick(null), "extended controller did not draw wireless EU");
            check(
                hatch.getAvailableEUBig()
                    .equals(balance.subtract(state.getEUtBig())),
                "wireless tick was not debited exactly once");
            WirelessNetworkManager.setUserEU(owner, BigInteger.ONE);
            int beforeProgress = controller.mProgresstime;
            check(!controller.onRunningTick(null) && state.isRunning(), "low balance discarded recipe");
            check(
                controller.mProgresstime == beforeProgress && hatch.getAvailableEUBig()
                    .equals(BigInteger.ONE),
                "rejected tick changed progress or balance");
            NBTTagCompound save = new NBTTagCompound();
            controller.saveNBTData(save);
            Controller restored = new Controller();
            restored.loadNBTData(save);
            WirelessRecipeState restoredState = ((BigWirelessController) (Object) restored).getWirelessRecipeState();
            check(
                restoredState.isRunning() && restoredState.getParallelsBig()
                    .equals(HUGE),
                "active exact recipe failed to reload");
            check(
                restoredState.getEUtBig()
                    .equals(state.getEUtBig()),
                "active recipe EU failed to reload");
            controller.mOutputBusses.clear();
            controller.mOutputHatches.clear();
            controller.complete();
            check(
                !state.isRunning() && state.pending()
                    .getItemAmountBig()
                    .equals(HUGE.multiply(BigInteger.valueOf(3))),
                "completed exact output lost without an output hatch");
            controller.complete();
            check(
                state.pending()
                    .getItemAmountBig()
                    .equals(HUGE.multiply(BigInteger.valueOf(3))),
                "duplicate completion duplicated output");
            WirelessRecipeState dropped = new WirelessRecipeState();
            dropped.load(state.saveProduced());
            check(
                !dropped.isRunning() && dropped.pending()
                    .getItemAmountBig()
                    .equals(
                        state.pending()
                            .getItemAmountBig()),
                "produced output failed to survive harvesting");
            restoredState.setParallelSettingBig(BigInteger.TEN.pow(25));
            WirelessRecipeState cap = new WirelessRecipeState();
            cap.load(restoredState.save());
            check(
                cap.getParallelSettingBig()
                    .equals(BigInteger.TEN.pow(25)),
                "manual parallel cap failed to reload");
            check(
                cap.getLimit()
                    .applyTo(HUGE)
                    .equals(BigInteger.TEN.pow(25)),
                "manual parallel cap ignored");
            verifyControls(owner, controller.recipes);
        } finally {
            GlobalVariableStorage.GlobalEnergy = oldEnergy;
            SpaceProjectManager.spaceTeams = oldTeams;
            GlobalEnergyWorldSavedData.INSTANCE = oldSave;
        }
        Apeiron.LOG.info(
            "Infinite energy hatch: shared 10^60 recipe, exact input debit, extended wireless tick, low-balance pause, persistence and output conservation verification passed");
    }

    private static Controller fixture(UUID owner, RecipeMap<?> recipes, boolean ultimate) {
        Controller machine = new Controller();
        machine.recipes = recipes;
        ApeironMachineTile energy = tile(ultimate ? ApeironMachines.ULTIMATE_ENERGY_HATCH_OFFSET : 3);
        energy.setOwnerUuid(owner);
        machine.mEnergyHatches.add((MTEInfiniteEnergyHatch) energy.getMetaTileEntity());
        machine.mOutputBusses.add((MTEBoundlessMEOutputBus) tile(0).getMetaTileEntity());
        machine.mWrench = machine.mScrewdriver = machine.mSoftMallet = machine.mHardHammer = machine.mSolderingTool = machine.mCrowbar = true;
        return machine;
    }

    private static MTEInfinitePatternInputAssembly input(Controller machine, BigInteger count) {
        MTEInfinitePatternInputAssembly input = (MTEInfinitePatternInputAssembly) tile(4).getMetaTileEntity();
        check(
            input.addToBufferBig(
                0,
                Arrays.asList(
                    BigAEStackValues.copyWithSize(
                        AEItemStack.create(new ItemStack(Items.diamond)),
                        count.multiply(BigInteger.valueOf(2))))),
            "control input setup");
        machine.mDualInputHatches.add(input);
        return input;
    }

    private static void runRecipe(Controller machine, MTEInfinitePatternInputAssembly input) {
        input.beginRecipeProcessing();
        try {
            gregtech.api.recipe.check.CheckRecipeResult result = machine.checkProcessing();
            check(result.wasSuccessful(), "control recipe failed: " + result.getID());
        } finally {
            input.endRecipeProcessing();
        }
    }

    private static void verifyControls(UUID owner, RecipeMap<?> recipes) {
        Controller bounded = fixture(owner, recipes, false);
        WirelessRecipeState boundedState = ((BigWirelessController) (Object) bounded).getWirelessRecipeState();
        boundedState.setParallelSettingBig(BigInteger.ZERO);
        MTEInfinitePatternInputAssembly boundedInput = input(bounded, HUGE);
        WirelessNetworkManager.setUserEU(owner, BigInteger.valueOf(800));
        runRecipe(bounded, boundedInput);
        check(
            boundedState.getParallelsBig()
                .equals(BigInteger.valueOf(5)),
            "whole-batch energy budget ignored");
        check(
            boundedInput.getBuffers()
                .get(0)
                .getItemAmountBig()
                .equals(
                    HUGE.subtract(BigInteger.valueOf(5))
                        .multiply(BigInteger.valueOf(2))),
            "budget consumed excess inputs");

        Controller physical = fixture(owner, recipes, false);
        physical.mOutputBusses.clear();
        gregtech.api.metatileentity.implementations.MTEHatchOutputBus nativeBus = new gregtech.api.metatileentity.implementations.MTEHatchOutputBus(
            "apeiron.verify.physical_output",
            1,
            1,
            new String[0],
            null);
        nativeBus.setBaseMetaTileEntity(new BaseMetaTileEntity());
        physical.mOutputBusses.add(nativeBus);
        WirelessRecipeState physicalState = ((BigWirelessController) (Object) physical).getWirelessRecipeState();
        physicalState.setParallelSettingBig(BigInteger.ZERO);
        MTEInfinitePatternInputAssembly physicalInput = input(physical, HUGE);
        WirelessNetworkManager.setUserEU(owner, HUGE.multiply(BigInteger.valueOf(160)));
        runRecipe(physical, physicalInput);
        check(
            physicalState.getParallelsBig()
                .equals(BigInteger.valueOf(21)),
            "one native slot did not constrain outputs to 64 items");
        check(nativeBus.getStackInSlot(0) == null, "output preflight committed an inventory mutation");

        Controller ultimate = fixture(owner, recipes, true);
        WirelessRecipeState ultimateState = ((BigWirelessController) (Object) ultimate).getWirelessRecipeState();
        check(ultimateState.getTargetDuration() == 128, "ultimate default is not 128 ticks");
        ultimateState.setParallelSettingBig(BigInteger.ZERO);
        ultimateState.setTargetDuration(7);
        MTEInfinitePatternInputAssembly ultimateInput = input(ultimate, BigInteger.valueOf(11));
        WirelessNetworkManager.setUserEU(owner, BigInteger.valueOf(1760));
        runRecipe(ultimate, ultimateInput);
        check(ultimate.mMaxProgresstime == 7 && ultimateState.isLossless(), "ultimate completion time ignored");
        check(
            ultimateState.getTotalEUBig()
                .equals(BigInteger.valueOf(1760)),
            "ultimate charged overclock rather than base recipe cost");
        check(ultimate.onRunningTick(null), "ultimate first tick");
        WirelessNetworkManager.setUserEU(owner, BigInteger.ZERO);
        BigInteger retryCost = ultimateState.nextDebit(10000);
        check(
            !ultimate.onRunningTick(null) && ultimateState.nextDebit(10000)
                .equals(retryCost),
            "pause advanced lossless schedule");
        NBTTagCompound saved = ultimateState.save();
        WirelessRecipeState reloaded = new WirelessRecipeState();
        reloaded.load(saved);
        check(
            reloaded.nextDebit(10000)
                .equals(retryCost),
            "lossless remainder failed to reload");
        BigInteger remaining = BigInteger.valueOf(1760)
            .subtract(BigInteger.valueOf(252));
        WirelessNetworkManager.setUserEU(owner, remaining);
        for (int tick = 1; tick < 7; tick++) check(ultimate.onRunningTick(null), "ultimate resumed tick");
        check(
            WirelessNetworkManager.getUserEU(owner)
                .signum() == 0,
            "lossless rounding added or lost EU");

        WirelessRecipeState legacyDefault = new WirelessRecipeState();
        NBTTagCompound old = new NBTTagCompound();
        old.setLong("parallelSetting", 0L);
        legacyDefault.load(old);
        check(
            legacyDefault.getParallelSettingBig()
                .equals(BigInteger.valueOf(Integer.MAX_VALUE)),
            "old automatic zero was not migrated");
        legacyDefault.setParallelSettingBig(BigInteger.ZERO);
        reloaded.load(legacyDefault.save());
        check(
            reloaded.getParallelSettingBig()
                .signum() == 0,
            "explicit zero failed to survive save");
        Apeiron.LOG.info(
            "Energy controls: full-batch budget, physical output capacity, lossless time, exact debit, pause/reload and safe defaults passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException("Infinite energy: " + message);
    }
}
