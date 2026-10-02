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
                    .signum() == 0 && state.getLimit()
                        .isUnlimited(),
                "zero does not mean unlimited");
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
        } finally {
            GlobalVariableStorage.GlobalEnergy = oldEnergy;
            SpaceProjectManager.spaceTeams = oldTeams;
            GlobalEnergyWorldSavedData.INSTANCE = oldSave;
        }
        Apeiron.LOG.info(
            "Infinite energy hatch: shared 10^60 recipe, exact input debit, extended wireless tick, low-balance pause, persistence and output conservation verification passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException("Infinite energy: " + message);
    }
}
