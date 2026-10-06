package com.silvia.apeiron.common.machine.energy.verification;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.api.machine.parallel.ItemProcessingRecipe;
import com.silvia.apeiron.api.machine.parallel.ItemProcessingRecipeSource;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.energy.MachineWailaSnapshot;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputBus;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.parallel.ItemProcessingRecipes;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;
import com.silvia.apeiron.mixin.gregtech.output.MultiBlockProcessingAccessor;

import appeng.util.item.AEItemStack;
import gregtech.api.enums.Materials;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;
import gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace;
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
            ItemSourceFixture.verify(owner);
        } finally {
            GlobalVariableStorage.GlobalEnergy = oldEnergy;
            SpaceProjectManager.spaceTeams = oldTeams;
            GlobalEnergyWorldSavedData.INSTANCE = oldSave;
        }
        Apeiron.LOG.info(
            "Wireless machine integrations: godforge, TST ore data and shared item-source dispatch/debit/output lifecycle passed");
    }

    private static final class ItemSourceFixture extends MTEElectricBlastFurnace {

        private RecipeMap<?> recipes;
        private int nativeChecks;
        private boolean sourceAllowed = true;

        private ItemSourceFixture() {
            super("apeiron.verify.item_source");
            setBaseMetaTileEntity(new BaseMetaTileEntity());
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return recipes;
        }

        @Override
        public CheckRecipeResult checkProcessing() {
            nativeChecks++;
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        @Override
        protected void sendStartMultiBlockSoundLoop() {}

        @Override
        public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x,
            int y, int z) {
            // The detached fixture has no player or world; use the same shared display writer as real controllers.
            com.silvia.apeiron.common.machine.energy.WirelessWailaDisplay
                .write(tag, ((BigWirelessController) (Object) this).getWirelessRecipeState(), mEfficiency);
        }

        static void verify(UUID owner) {
            RecipeMap<?> recipeMap = RecipeMapBuilder.of("apeiron.verify.item_source_recipes")
                .maxIO(1, 1, 0, 0)
                .build();
            ItemProcessingRecipes.register(recipeMap, new ItemProcessingRecipeSource() {

                private final ItemProcessingRecipe recipe = createRecipe();

                private ItemProcessingRecipe createRecipe() {
                    BigMachineOutputQueue outputs = new BigMachineOutputQueue();
                    outputs.addItem(new ItemStack(Items.emerald), BigInteger.valueOf(5));
                    return new ItemProcessingRecipe(BigInteger.valueOf(11), outputs);
                }

                @Override
                public CheckRecipeResult validate(MTEMultiBlockBase machine) {
                    return ((ItemSourceFixture) machine).sourceAllowed ? CheckRecipeResultRegistry.SUCCESSFUL
                        : CheckRecipeResultRegistry.NO_RECIPE;
                }

                @Override
                public int getDurationTicks() {
                    return 7;
                }

                @Override
                public ItemProcessingRecipe findRecipe(ItemStack input) {
                    if (input.getItem() != Items.diamond) return null;
                    return recipe;
                }
            });
            for (int pass = 0; pass < 2; pass++) {
                ItemSourceFixture machine = new ItemSourceFixture();
                machine.recipes = recipeMap;
                MTEInfinitePatternInputAssembly input = attach(machine, owner, 3);
                WirelessNetworkManager.setUserEU(owner, HUGE.multiply(BigInteger.valueOf(11)));
                check(
                    ((MultiBlockProcessingAccessor) (Object) machine).apeiron$checkRecipe(),
                    "second controller failed shared dispatch");
                check(machine.nativeChecks == 0, "source dispatch entered the custom native processor");
                WirelessRecipeState state = ((BigWirelessController) (Object) machine).getWirelessRecipeState();
                check(
                    state.getDuration() == 7 && state.getParallelsBig()
                        .equals(HUGE),
                    "source data was not applied");
                check(
                    state.getTotalEUBig()
                        .equals(HUGE.multiply(BigInteger.valueOf(11))),
                    "source cost was truncated");
                check(
                    input.getBuffers()
                        .get(0)
                        .isEmpty(),
                    "shared dispatch did not commit exact inputs");
                NBTTagCompound saved = new NBTTagCompound();
                machine.saveNBTData(saved);
                ItemSourceFixture restored = new ItemSourceFixture();
                restored.recipes = recipeMap;
                restored.loadNBTData(saved);
                WirelessRecipeState loaded = ((BigWirelessController) (Object) restored).getWirelessRecipeState();
                check(
                    loaded.isRunning() && loaded.getOutputDisplay()
                        .size() == 1
                        && loaded.getTotalEUBig()
                            .equals(state.getTotalEUBig()),
                    "shared source state did not reload");
                NBTTagCompound snapshot = new NBTTagCompound();
                check(
                    ItemProcessingRecipes.hasSource(machine.getRecipeMap())
                        && MachineWailaSnapshot
                            .write(null, (BaseMetaTileEntity) machine.getBaseMetaTileEntity(), snapshot)
                        && snapshot.getBoolean("ApeironWirelessRunning"),
                    "shared source Waila snapshot was unavailable");
                for (int tick = 0; tick < state.getDuration(); tick++)
                    check(machine.onRunningTick(null), "source debit failed");
                check(
                    WirelessNetworkManager.getUserEU(owner)
                        .signum() == 0,
                    "source tick remainder was lost");
                machine.outputAfterRecipe();
                check(
                    !state.isRunning() && ((MTEBoundlessMEOutputBus) machine.mOutputBusses.get(0)).getProvider()
                        .getCachedAmountBig()
                        .add(
                            state.pending()
                                .getItemAmountBig())
                        .equals(HUGE.multiply(BigInteger.valueOf(5))),
                    "second source did not use shared completion");
            }
            verifyFallbackAndValidation(owner, recipeMap);
            verifyOutputErrors(owner);
        }

        private static void verifyOutputErrors(UUID owner) {
            RecipeMap<?> map = RecipeMapBuilder.of("apeiron.verify.item_source_output_errors")
                .maxIO(1, 1, 0, 1)
                .build();
            ItemProcessingRecipes.register(map, new ItemProcessingRecipeSource() {

                @Override
                public int getDurationTicks() {
                    return 7;
                }

                @Override
                public ItemProcessingRecipe findRecipe(ItemStack input) {
                    if (input.getItem() != Items.diamond) return null;
                    BigMachineOutputQueue outputs = new BigMachineOutputQueue();
                    outputs.addItem(new ItemStack(Items.emerald), BigInteger.ONE);
                    outputs.addFluid(
                        new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 1),
                        BigInteger.valueOf(7));
                    return new ItemProcessingRecipe(BigInteger.valueOf(11), outputs);
                }
            });
            for (boolean missingItems : new boolean[] { false, true }) {
                ItemSourceFixture machine = new ItemSourceFixture();
                machine.recipes = map;
                MTEInfinitePatternInputAssembly input = attach(machine, owner, 3);
                if (missingItems) {
                    machine.mOutputBusses.clear();
                    machine.mOutputHatches.add(
                        (com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputHatch) tile(1, owner)
                            .getMetaTileEntity());
                }
                BigInteger balance = HUGE.multiply(BigInteger.valueOf(11));
                WirelessNetworkManager.setUserEU(owner, balance);
                check(
                    !((MultiBlockProcessingAccessor) (Object) machine).apeiron$checkRecipe(),
                    "item-source capacity accepted missing channel");
                check(
                    machine.getCheckRecipeResult()
                        .equals(
                            missingItems ? CheckRecipeResultRegistry.ITEM_OUTPUT_FULL
                                : CheckRecipeResultRegistry.FLUID_OUTPUT_FULL),
                    "item-source capacity reported the wrong output channel");
                check(
                    input.getBuffers()
                        .get(0)
                        .getItemAmountBig()
                        .equals(HUGE)
                        && WirelessNetworkManager.getUserEU(owner)
                            .equals(balance),
                    "item-source capacity failure consumed input or energy");
            }
        }

        private static void verifyFallbackAndValidation(UUID owner, RecipeMap<?> recipeMap) {
            ItemSourceFixture machine = new ItemSourceFixture();
            machine.recipes = recipeMap;
            MTEInfinitePatternInputAssembly input = attach(machine, owner, 3);
            WirelessNetworkManager.setUserEU(owner, HUGE.multiply(BigInteger.valueOf(11)));
            machine.sourceAllowed = false;
            check(
                !((MultiBlockProcessingAccessor) (Object) machine).apeiron$checkRecipe(),
                "startup condition ignored");
            check(
                machine.nativeChecks == 0 && input.getBuffers()
                    .get(0)
                    .getItemAmountBig()
                    .equals(HUGE),
                "rejected source consumed input or fell through to native processing");
            machine.sourceAllowed = true;
            machine.mEnergyHatches.clear();
            check(
                !((MultiBlockProcessingAccessor) (Object) machine).apeiron$checkRecipe() && machine.nativeChecks == 1,
                "controller without an infinite hatch did not use its native processor");
            machine.recipes = null;
            machine.mEnergyHatches.add((MTEInfiniteEnergyHatch) tile(3, owner).getMetaTileEntity());
            check(
                !((MultiBlockProcessingAccessor) (Object) machine).apeiron$checkRecipe() && machine.nativeChecks == 2,
                "unregistered recipe map did not use its native processor");
            check(
                input.getBuffers()
                    .get(0)
                    .getItemAmountBig()
                    .equals(HUGE)
                    && WirelessNetworkManager.getUserEU(owner)
                        .equals(HUGE.multiply(BigInteger.valueOf(11))),
                "native fallback changed exact source input or energy");
        }
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
                    "apeiron.verify.ore_factory") {

                    @Override
                    protected void sendStartMultiBlockSoundLoop() {}
                };
                machine.setBaseMetaTileEntity(new BaseMetaTileEntity());
                MTEInfinitePatternInputAssembly input = attach(machine, owner, 3);
                WirelessNetworkManager.setUserEU(owner, HUGE.multiply(BigInteger.valueOf(3840)));
                ItemStack returned = new ItemStack(Items.apple);
                com.Nxer.TwistSpaceTechnology.util.rewrites.TST_ItemID returnedType = com.Nxer.TwistSpaceTechnology.util.rewrites.TST_ItemID
                    .create(returned);
                check(
                    !com.Nxer.TwistSpaceTechnology.system.OreProcess.logic.OP_Logic.OP_GIVER_MAP
                        .containsKey(returnedType),
                    "return fixture has a transformation");
                input.addToBufferBig(
                    0,
                    Arrays.asList(BigAEStackValues.copyWithSize(AEItemStack.create(returned), BigInteger.valueOf(5))));
                check(
                    ((MultiBlockProcessingAccessor) (Object) machine).apeiron$checkRecipe(),
                    "ore data did not use shared recipe caller");
                com.silvia.apeiron.common.machine.parallel.WirelessRecipeState state = ((BigWirelessController) (Object) machine)
                    .getWirelessRecipeState();
                check(
                    state.isRunning() && state.getParallelsBig()
                        .equals(HUGE.add(BigInteger.valueOf(5))),
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
                    outputs.equals(
                        HUGE.multiply(BigInteger.valueOf(3))
                            .add(BigInteger.valueOf(5))),
                    "ore factory completion lost exact outputs");
                check(
                    input.getBuffers()
                        .get(0)
                        .isEmpty(),
                    "ore factory exact inputs were not consumed");
                verifyLubricant(machine, input, owner);
                verifyLiveRecipeData(machine, input, owner, type);
            } finally {
                if (previous == null)
                    com.Nxer.TwistSpaceTechnology.system.OreProcess.logic.OP_Logic.OP_GIVER_MAP.remove(type);
                else com.Nxer.TwistSpaceTechnology.system.OreProcess.logic.OP_Logic.OP_GIVER_MAP.put(type, previous);
            }
        }
    }

    private static void verifyLubricant(
        com.Nxer.TwistSpaceTechnology.system.OreProcess.machines.TST_OreProcessingFactory machine,
        MTEInfinitePatternInputAssembly input, UUID owner) {
        machine.mOutputBusses.clear();
        input.addToBufferBig(
            0,
            Arrays.asList(
                BigAEStackValues.copyWithSize(AEItemStack.create(new ItemStack(Items.diamond)), BigInteger.ONE)));
        WirelessNetworkManager.setUserEU(owner, BigInteger.valueOf(3840));
        check(
            !((MultiBlockProcessingAccessor) (Object) machine).apeiron$checkRecipe(),
            "missing output receiver accepted");
        check(
            input.getBuffers()
                .get(0)
                .getItemAmountBig()
                .equals(BigInteger.ONE),
            "output rejection spent input");
        machine.mOutputBusses.add((MTEBoundlessMEOutputBus) tile(0, owner).getMetaTileEntity());
        check(((MultiBlockProcessingAccessor) (Object) machine).apeiron$checkRecipe(), "second native cycle failed");
        MTEHatchInput lubricant = new MTEHatchInput("apeiron.verify.lubricant", 10, new String[0], null);
        lubricant.setBaseMetaTileEntity(new BaseMetaTileEntity());
        lubricant.mFluid = Materials.Lubricant.getFluid(4000);
        machine.mInputHatches.add(lubricant);
        for (int tick = 0; tick < 128; tick++) check(machine.onRunningTick(null), "native lubricant callback failed");
        check(lubricant.mFluid.amount == 800, "native periodic lubricant consumption changed");
    }

    private static void verifyLiveRecipeData(
        com.Nxer.TwistSpaceTechnology.system.OreProcess.machines.TST_OreProcessingFactory machine,
        MTEInfinitePatternInputAssembly input, UUID owner,
        com.Nxer.TwistSpaceTechnology.util.rewrites.TST_ItemID type) {
        WirelessRecipeState state = ((BigWirelessController) (Object) machine).getWirelessRecipeState();
        state.cancelRecipe();
        com.Nxer.TwistSpaceTechnology.common.api.giver.ItemStacksGiver changed = new com.Nxer.TwistSpaceTechnology.common.api.giver.ItemStacksGiver();
        changed.cache.put(
            com.Nxer.TwistSpaceTechnology.util.rewrites.TST_ItemID.create(new ItemStack(Items.emerald)),
            Long.MAX_VALUE);
        com.Nxer.TwistSpaceTechnology.system.OreProcess.logic.OP_Logic.OP_GIVER_MAP.put(type, changed);
        input.addToBufferBig(
            0,
            Arrays.asList(
                BigAEStackValues
                    .copyWithSize(AEItemStack.create(new ItemStack(Items.diamond)), BigInteger.valueOf(2))));
        WirelessNetworkManager.setUserEU(owner, BigInteger.valueOf(7680));
        check(
            ((MultiBlockProcessingAccessor) (Object) machine).apeiron$checkRecipe(),
            "live source replacement was not matched");
        check(
            BigAEStackValues.get(
                state.getOutputDisplay()
                    .get(0))
                .equals(
                    BigInteger.valueOf(Long.MAX_VALUE)
                        .multiply(BigInteger.valueOf(2))),
            "live output factors were cached or projected through int/long multiplication");
        state.cancelRecipe();
        com.Nxer.TwistSpaceTechnology.system.OreProcess.logic.OP_Logic.OP_GIVER_MAP.remove(type);
        input.addToBufferBig(
            0,
            Arrays.asList(
                BigAEStackValues.copyWithSize(AEItemStack.create(new ItemStack(Items.diamond)), BigInteger.ONE)));
        WirelessNetworkManager.setUserEU(owner, BigInteger.ZERO);
        check(
            ((MultiBlockProcessingAccessor) (Object) machine).apeiron$checkRecipe(),
            "removed recipe did not use the unmatched-item rule");
        check(
            state.getTotalEUBig()
                .signum() == 0
                && state.getOutputDisplay()
                    .size() == 1
                && ((appeng.api.storage.data.IAEItemStack) state.getOutputDisplay()
                    .get(0)).getItemStack()
                        .getItem()
                    == Items.diamond,
            "removed source recipe retained cached output or cost");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
