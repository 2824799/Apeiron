package com.silvia.apeiron.common.machine.energy.verification;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputBus;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;

import appeng.util.item.AEItemStack;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.util.GTRecipe;
import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;
import tectech.thing.metaTileEntity.multi.godforge.MTESmeltingModule;

/** Real transformed module and ore-factory checks; accounts and all test inventories are detached. */
public final class WirelessMachineIntegrationSmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(30)
        .add(BigInteger.valueOf(11));

    private WirelessMachineIntegrationSmoke() {}

    private static final class Module extends MTESmeltingModule {

        private RecipeMap<?> recipes;

        Module(UUID owner) {
            super("apeiron.verify.godforge");
            setBaseMetaTileEntity(new BaseMetaTileEntity());
            userUUID = owner;
            setHeat(10000);
            setHeatForOC(10000);
            setSpeedBonus(1);
            setEnergyDiscount(1);
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return recipes;
        }

        void complete() {
            addClassicOutputs_EM();
        }
    }

    private static ApeironMachineTile tile(int offset, UUID owner) {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(offset));
        tile.setOwnerUuid(owner);
        return tile;
    }

    private static MTEInfinitePatternInputAssembly attach(MTEMultiBlockBase machine, UUID owner, int energyOffset) {
        machine.mEnergyHatches.add((MTEInfiniteEnergyHatch) tile(energyOffset, owner).getMetaTileEntity());
        machine.mOutputBusses.add((MTEBoundlessMEOutputBus) tile(0, owner).getMetaTileEntity());
        MTEInfinitePatternInputAssembly input = (MTEInfinitePatternInputAssembly) tile(4, owner).getMetaTileEntity();
        check(
            input.addToBufferBig(
                0,
                Arrays.asList(BigAEStackValues.copyWithSize(AEItemStack.create(new ItemStack(Items.diamond)), HUGE))),
            "integration input setup");
        machine.mDualInputHatches.add(input);
        machine.mWrench = machine.mScrewdriver = machine.mSoftMallet = machine.mHardHammer = machine.mSolderingTool = machine.mCrowbar = true;
        ((BigWirelessController) machine).getWirelessRecipeState()
            .setParallelSettingBig(BigInteger.ZERO);
        return input;
    }

    private static void process(MTEMultiBlockBase machine, MTEInfinitePatternInputAssembly input) {
        input.beginRecipeProcessing();
        try {
            gregtech.api.recipe.check.CheckRecipeResult result = machine.checkProcessing();
            check(
                result.wasSuccessful(),
                machine.getClass()
                    .getSimpleName() + " rejected recipe: "
                    + result.getID());
        } finally {
            input.endRecipeProcessing();
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
            UUID owner = UUID.randomUUID();
            Module module = new Module(owner);
            module.recipes = RecipeMapBuilder.of("apeiron.verify.module_recipes")
                .maxIO(1, 1, 0, 0)
                .build();
            module.recipes.addRecipe(
                new GTRecipe(
                    false,
                    new ItemStack[] { new ItemStack(Items.diamond) },
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
            MTEInfinitePatternInputAssembly moduleInput = attach(
                module,
                owner,
                ApeironMachines.ULTIMATE_ENERGY_HATCH_OFFSET);
            WirelessNetworkManager.setUserEU(owner, HUGE.multiply(BigInteger.valueOf(160)));
            process(module, moduleInput);
            com.silvia.apeiron.common.machine.parallel.WirelessRecipeState state = ((BigWirelessController) (Object) module)
                .getWirelessRecipeState();
            check(
                state.isRunning() && state.getParallelsBig()
                    .equals(HUGE),
                "godforge retained its native parallel cap");
            check(module.mMaxProgresstime == 128, "godforge ignored ultimate completion time");
            check(
                WirelessNetworkManager.getUserEU(owner)
                    .equals(HUGE.multiply(BigInteger.valueOf(160))),
                "godforge double-charged startup power");
            check(module.onRunningTick(null), "godforge did not use wireless running debit");
            module.complete();
            check(!state.isRunning(), "godforge did not finish exact output lifecycle");
            if (cpw.mods.fml.common.Loader.isModLoaded("TwistSpaceTechnology")) OreFixture.verify(owner);
        } finally {
            GlobalVariableStorage.GlobalEnergy = oldEnergy;
            SpaceProjectManager.spaceTeams = oldTeams;
            GlobalEnergyWorldSavedData.INSTANCE = oldSave;
        }
        Apeiron.LOG.info(
            "Wireless machine integrations: actual godforge and TST ore factory exact parallel/debit/output lifecycle passed");
    }

    private static final class OreFixture {

        static void verify(UUID owner) {
            com.Nxer.TwistSpaceTechnology.util.rewrites.TST_ItemID type = com.Nxer.TwistSpaceTechnology.util.rewrites.TST_ItemID
                .create(new ItemStack(Items.diamond));
            com.Nxer.TwistSpaceTechnology.common.api.giver.ItemStacksGiver previous = com.Nxer.TwistSpaceTechnology.system.OreProcess.logic.OP_Logic.OP_GIVER_MAP
                .put(
                    type,
                    com.Nxer.TwistSpaceTechnology.common.api.giver.ItemStacksGiver
                        .create(new ItemStack(Items.emerald, 3)));
            try {
                com.Nxer.TwistSpaceTechnology.system.OreProcess.machines.TST_OreProcessingFactory machine = new com.Nxer.TwistSpaceTechnology.system.OreProcess.machines.TST_OreProcessingFactory(
                    "apeiron.verify.ore_factory");
                machine.setBaseMetaTileEntity(new BaseMetaTileEntity());
                MTEInfinitePatternInputAssembly input = attach(machine, owner, 3);
                WirelessNetworkManager.setUserEU(owner, HUGE.multiply(BigInteger.valueOf(3840)));
                process(machine, input);
                com.silvia.apeiron.common.machine.parallel.WirelessRecipeState state = ((BigWirelessController) (Object) machine)
                    .getWirelessRecipeState();
                check(
                    state.isRunning() && state.getParallelsBig()
                        .equals(HUGE),
                    "ore factory remained in the native int/long loop");
                check(
                    state.getTotalEUBig()
                        .equals(HUGE.multiply(BigInteger.valueOf(3840))),
                    "ore-factory energy mismatch");
                BigInteger before = WirelessNetworkManager.getUserEU(owner);
                BigInteger debit = state.nextDebit(machine.mEfficiency);
                check(machine.onRunningTick(null), "ore factory wireless tick failed");
                check(
                    WirelessNetworkManager.getUserEU(owner)
                        .equals(before.subtract(debit))
                        && state.save()
                            .getInteger("paidTicks") == 1,
                    "ore factory wireless tick did not debit the exact state");
                WirelessNetworkManager.setUserEU(owner, BigInteger.ZERO);
                BigInteger retryDebit = state.nextDebit(machine.mEfficiency);
                check(
                    !machine.onRunningTick(null) && state.nextDebit(machine.mEfficiency)
                        .equals(retryDebit)
                        && state.save()
                            .getInteger("paidTicks") == 1,
                    "ore factory pause advanced or charged the debit schedule");
                WirelessNetworkManager.setUserEU(owner, before.subtract(debit));
                for (int tick = 1; tick < state.getDuration(); tick++)
                    check(machine.onRunningTick(null), "ore factory resume debit failed");
                check(
                    WirelessNetworkManager.getUserEU(owner)
                        .signum() == 0,
                    "ore factory whole-recipe debit was repeated or omitted");
                com.silvia.apeiron.common.machine.energy.WirelessControllerEnergy.complete(machine);
                check(!state.isRunning(), "ore factory output lifecycle did not finish");
                BigInteger outputs = ((com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputBus) machine.mOutputBusses
                    .get(0)).getProvider()
                        .getCachedAmountBig()
                        .add(
                            state.pending()
                                .getItemAmountBig());
                check(
                    outputs.equals(HUGE.multiply(BigInteger.valueOf(3))),
                    "ore factory completion lost exact outputs");
                check(
                    input.getBuffers()
                        .get(0)
                        .isEmpty(),
                    "ore factory exact inputs were not consumed");
            } finally {
                if (previous == null)
                    com.Nxer.TwistSpaceTechnology.system.OreProcess.logic.OP_Logic.OP_GIVER_MAP.remove(type);
                else com.Nxer.TwistSpaceTechnology.system.OreProcess.logic.OP_Logic.OP_GIVER_MAP.put(type, previous);
            }
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
