package com.silvia.apeiron.common.integration.gtnl.parallel;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.science.gtnl.common.machine.multiblock.wireless.ExtremeElectricFurnace;
import com.science.gtnl.common.machine.multiblock.wireless.HighwayToHell;
import com.science.gtnl.utils.recipes.GTNLOverclockCalculator;
import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.energy.WirelessControllerEnergy;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;

import appeng.util.item.AEItemStack;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTRecipe;
import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;

/** Real transformed GTNL loops and public inventories; tests use detached machine tiles and isolated EU accounts. */
public final class GtnlIntegrationSmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(22)
        .add(BigInteger.valueOf(19));
    private static int maps;

    private GtnlIntegrationSmoke() {}

    private static final class Crusher extends HighwayToHell {

        private final RecipeMap<?> recipes;
        private int limit = 32;

        Crusher(UUID owner) {
            super("apeiron.verify.gtnl.crusher");
            recipes = RecipeMapBuilder.of("apeiron.verify.gtnl.crusher." + maps++)
                .maxIO(1, 1, 0, 0)
                .build();
            attachController(this, owner);
            mGlassTier = 14;
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return recipes;
        }

        @Override
        public int getMaxParallelRecipes() {
            return limit;
        }

        @Override
        public void resetParallelTier() {
            mParallelTier = 15;
        }
    }

    private static final class Furnace extends ExtremeElectricFurnace {

        private final RecipeMap<?> recipes;
        private int limit = 8;

        Furnace(UUID owner) {
            super("apeiron.verify.gtnl.furnace");
            recipes = RecipeMapBuilder.of("apeiron.verify.gtnl.furnace." + maps++)
                .maxIO(1, 1, 0, 1)
                .build();
            attachController(this, owner);
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return recipes;
        }

        @Override
        public int getMaxParallelRecipes() {
            return limit;
        }

        @Override
        public void stopMachine(gregtech.api.util.shutdown.ShutDownReason reason) {
            // Detached verification tiles have no world for the native shutdown sound/event.
            state(this).cancelRecipe();
            mMaxProgresstime = 0;
            lEUt = 0;
        }
    }

    private static final class RealCrusher extends HighwayToHell {

        private final RecipeMap<?> recipes;

        RealCrusher(UUID owner) {
            super("apeiron.verify.gtnl.real_controls");
            recipes = RecipeMapBuilder.of("apeiron.verify.gtnl.real_controls." + maps++)
                .maxIO(1, 1, 0, 0)
                .build();
            attachController(this, owner);
            mGlassTier = 14;
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return recipes;
        }
    }

    private static void attachController(MTEMultiBlockBase machine, UUID owner) {
        machine.setBaseMetaTileEntity(new BaseMetaTileEntity());
        machine.getBaseMetaTileEntity()
            .setOwnerUuid(owner);
        ((com.science.gtnl.common.machine.multiMachineBase.WirelessEnergyMultiMachineBase<?>) machine).ownerUUID = owner;
        machine.mWrench = machine.mScrewdriver = machine.mSoftMallet = machine.mHardHammer = machine.mSolderingTool = machine.mCrowbar = true;
    }

    private static ApeironMachineTile tile(int offset, UUID owner) {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(offset));
        tile.setOwnerUuid(owner);
        return tile;
    }

    private static MTEInfiniteMEOutputAssembly output(MTEMultiBlockBase machine, UUID owner) {
        MTEInfiniteMEOutputAssembly output = (MTEInfiniteMEOutputAssembly) tile(
            ApeironMachines.MIXED_OUTPUT_ASSEMBLY_OFFSET,
            owner).getMetaTileEntity();
        check(
            machine.addToMachineList(output.getBaseMetaTileEntity(), 0),
            "GTNL structure scanner rejected mixed output");
        return output;
    }

    private static void energy(MTEMultiBlockBase machine, UUID owner, boolean ultimate) {
        machine.mEnergyHatches.add(
            (MTEInfiniteEnergyHatch) tile(
                ultimate ? ApeironMachines.ULTIMATE_ENERGY_HATCH_OFFSET : ApeironMachines.INFINITE_ENERGY_HATCH_OFFSET,
                owner).getMetaTileEntity());
    }

    private static WirelessRecipeState state(MTEMultiBlockBase machine) {
        return ((BigWirelessController) machine).getWirelessRecipeState();
    }

    private static GTRecipe recipe(ItemStack input, ItemStack output, int ticks, int eu, boolean water) {
        return gregtech.api.enums.GTValues.RA.stdBuilder()
            .itemInputs(input)
            .itemOutputs(output)
            .fluidOutputs(water ? new FluidStack[] { new FluidStack(FluidRegistry.WATER, 1000) } : new FluidStack[0])
            .duration(ticks)
            .eut(eu)
            .build()
            .get();
    }

    public static void verify() {
        HashMap<UUID, BigInteger> oldEnergy = GlobalVariableStorage.GlobalEnergy;
        Map<UUID, UUID> oldTeams = SpaceProjectManager.spaceTeams;
        GlobalEnergyWorldSavedData oldSave = GlobalEnergyWorldSavedData.INSTANCE;
        try {
            GlobalVariableStorage.GlobalEnergy = new HashMap<>();
            SpaceProjectManager.spaceTeams = new HashMap<>();
            GlobalEnergyWorldSavedData.INSTANCE = new GlobalEnergyWorldSavedData();
            verifyOverclocks();
            verifyOutputEffects();
            for (int ticks : new int[] { 1, 128 })
                for (BigInteger parallels : new BigInteger[] { BigInteger.ONE, BigInteger.valueOf(37), HUGE })
                    verifyCrusher(parallels, ticks);
            verifyNativeCrusher();
            for (boolean batch : new boolean[] { false, true }) {
                verifyNativeControls(14, 1, 0, batch);
                verifyNativeControls(14, 1, 1, batch);
                verifyNativeControls(14, 37, 0, batch);
                verifyNativeControls(3, 1, 1, batch);
                verifyNativeControls(14, 1, 1, batch, true);
                verifyNativeControls(14, 37, 0, batch, true);
            }
            verifyGeneric(false);
            verifyGeneric(true);
            verifyMissingOutput();
            verifyLowEnergy();
            verifyNativeWired();
            verifyUnmodifiedMachine();
        } finally {
            GlobalVariableStorage.GlobalEnergy = oldEnergy;
            SpaceProjectManager.spaceTeams = oldTeams;
            GlobalEnergyWorldSavedData.INSTANCE = oldSave;
        }
        Apeiron.LOG.info(
            "GTNL exact cross-recipe verification passed: ultimate without upgrade, adjustable big parallel/time, native wireless exact debit, ordinary voltage, item/fluid conservation, missing outputs, native power and save/reload");
    }

    private static void verifyOverclocks() {
        for (boolean amperage : new boolean[] { false, true })
            for (int tier = 1; tier < 10; tier++) for (int count : new int[] { 1, 8, 32 }) {
                GTNLOverclockCalculator nativeCalc = new GTNLOverclockCalculator().setRecipeEUt(32)
                    .setDuration(200)
                    .setEUt(8L << (2 * tier))
                    .setAmperage(4)
                    .setAmperageOC(amperage)
                    .setEUtDiscount(0.5)
                    .setDurationModifier(0.5)
                    .setParallel(count)
                    .setCurrentParallel(count);
                GtnlOverclocks exact = GtnlOverclocks.calculate(nativeCalc, BigInteger.valueOf(count));
                check(
                    GtnlOverclocks.maximum(nativeCalc, count)
                        .equals(BigInteger.valueOf((long) (count * nativeCalc.calculateMultiplierUnderOneTick()))),
                    "native sub-tick multiplier changed at tier " + tier + " lanes " + count);
                nativeCalc.calculate();
                check(
                    exact.eut.equals(BigInteger.valueOf(nativeCalc.getConsumption()))
                        && exact.duration == nativeCalc.getDuration(),
                    "native OC behavior changed at tier " + tier + " lanes " + count);
            }
        GTNLOverclockCalculator large = new GTNLOverclockCalculator().setRecipeEUt(Long.MAX_VALUE)
            .setDuration(2)
            .setEUt(Long.MAX_VALUE)
            .setNoOverclock(true)
            .setDurationModifier(1.25)
            .setEUtDiscount(0.45);
        GtnlOverclocks full = GtnlOverclocks.calculate(large, BigInteger.valueOf(Integer.MAX_VALUE));
        check(
            full.eut.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 && full.duration == 3,
            "large energy or no-OC duration was narrowed");
    }

    private static void verifyOutputEffects() {
        com.science.gtnl.common.machine.multiblock.wireless.TreeDiagram tree = new com.science.gtnl.common.machine.multiblock.wireless.TreeDiagram(
            "apeiron.verify.gtnl.nanites");
        tree.failureBonus = 1;
        tree.outputCoefficient = 1;
        BigInteger amount = HUGE.multiply(BigInteger.valueOf(4));
        check(
            new GtnlOutputEffects(tree, HUGE, false).apply(amount)
                .equals(HUGE.multiply(BigInteger.valueOf(5))),
            "nanite output effects narrowed big counts");
        check(
            new GtnlOutputEffects(tree, HUGE, true).apply(amount)
                .equals(HUGE.multiply(BigInteger.valueOf(6))),
            "nanite capacity planning was not conservative");
        com.science.gtnl.common.machine.multiblock.wireless.CrackerHub cracker = new com.science.gtnl.common.machine.multiblock.wireless.CrackerHub(
            "apeiron.verify.gtnl.coils");
        cracker.setMCoilLevel(gregtech.api.enums.HeatingCoilLevel.IV);
        GtnlOutputEffects coils = new GtnlOutputEffects(cracker, HUGE, false);
        check(
            coils.apply(amount)
                .equals(amount),
            "coil item multiplier no longer matches native integer rule");
        check(
            coils.applyFluid(amount)
                .equals(
                    amount.multiply(BigInteger.valueOf(6))
                        .divide(BigInteger.valueOf(5))),
            "fractional coil fluid bonus was truncated");
        com.science.gtnl.common.machine.multiblock.PCBFactory pcb = new com.science.gtnl.common.machine.multiblock.PCBFactory(
            "apeiron.verify.gtnl.pcb");
        GtnlOutputEffects pcbOutputs = new GtnlOutputEffects(pcb, HUGE, false);
        check(
            pcbOutputs.apply(amount)
                .equals(amount.multiply(BigInteger.valueOf(2)))
                && pcbOutputs.applyFluid(amount)
                    .equals(amount),
            "PCB native item bonus changed or leaked into fluids");
    }

    private static void verifyCrusher(BigInteger parallels, int ticks) {
        UUID owner = UUID.randomUUID();
        BigInteger initial = BigInteger.TEN.pow(60);
        WirelessNetworkManager.addEUToGlobalEnergyMap(owner, initial);
        Crusher machine = new Crusher(owner);
        energy(machine, owner, true);
        MTEInfiniteMEOutputAssembly output = output(machine, owner);
        MTEHatchInputBus input = new MTEHatchInputBus("apeiron.verify.gtnl.catalysts", 1, new String[0], null);
        input.setBaseMetaTileEntity(new BaseMetaTileEntity());
        input.setInventorySlotContents(0, new ItemStack(Items.diamond));
        input.setInventorySlotContents(1, new ItemStack(Items.iron_ingot));
        machine.mInputBusses.add(input);
        machine.recipes
            .addRecipe(recipe(new ItemStack(Items.diamond, 0), new ItemStack(Items.emerald, 2), 20, 32, false));
        machine.recipes
            .addRecipe(recipe(new ItemStack(Items.iron_ingot, 0), new ItemStack(Items.apple, 3), 40, 64, false));
        state(machine).setParallelSettingBig(parallels);
        state(machine).setTargetDuration(ticks);
        machine.setWirelessMode(false);
        check(machine.wirelessMode && !machine.wirelessUpgrade, "ultimate mode requires native upgrade");
        CheckRecipeResult result = machine.checkProcessing();
        check(result.wasSuccessful(), "ultimate crusher rejected cross-recipe batch: " + result.getID());
        BigInteger total = parallels.multiply(BigInteger.valueOf(3200));
        check(
            state(machine).getParallelsBig()
                .equals(parallels.multiply(BigInteger.valueOf(2))),
            "cross recipe parallel count truncated");
        check(
            state(machine).getTotalEUBig()
                .equals(total) && machine.costingEU.equals(total),
            "ultimate batch energy truncated");
        check(
            machine.mMaxProgresstime == ticks && WirelessNetworkManager.getUserEU(owner)
                .equals(initial),
            "duration not locked or startup double debit");
        WirelessRecipeState restored = new WirelessRecipeState();
        restored.load(state(machine).save());
        check(
            restored.getTotalEUBig()
                .equals(total)
                && restored.getOutputDisplay()
                    .size() == 2,
            "big cross-recipe state was lost in save");
        for (int tick = 0; tick < ticks; tick++)
            check(WirelessControllerEnergy.debitTick(machine), "ultimate tick debit failed");
        check(
            WirelessNetworkManager.getUserEU(owner)
                .equals(initial.subtract(total)),
            "ultimate total debit mismatch");
        WirelessControllerEnergy.complete(machine);
        check(
            output.getProvider()
                .getCachedAmountBig()
                .equals(parallels.multiply(BigInteger.valueOf(5))),
            "big cross recipe output truncated");
        check(input.getStackInSlot(0).stackSize == 1 && input.getStackInSlot(1).stackSize == 1, "catalysts consumed");
    }

    private static void verifyNativeCrusher() {
        UUID owner = UUID.randomUUID();
        BigInteger initial = BigInteger.TEN.pow(60);
        WirelessNetworkManager.addEUToGlobalEnergyMap(owner, initial);
        Crusher machine = new Crusher(owner);
        machine.limit = 1 << 24;
        MTEInfiniteMEOutputAssembly output = output(machine, owner);
        MTEHatchInputBus input = new MTEHatchInputBus("apeiron.verify.gtnl.native", 1, new String[0], null);
        input.setBaseMetaTileEntity(new BaseMetaTileEntity());
        input.setInventorySlotContents(0, new ItemStack(Items.diamond));
        machine.mInputBusses.add(input);
        machine.recipes
            .addRecipe(recipe(new ItemStack(Items.diamond, 0), new ItemStack(Items.emerald, 4096), 20, 32, false));
        machine.wirelessUpgrade = true;
        machine.setWirelessMode(true);
        CheckRecipeResult result = machine.checkProcessing();
        check(result.wasSuccessful(), "native wireless crusher failed: " + result.getID());
        check(
            machine.costingEU.equals(initial.subtract(WirelessNetworkManager.getUserEU(owner))),
            "native exact cost did not match account debit");
        check(state(machine).usesNativeEnergy(), "native wireless batch attempted tick debit");
        BigInteger expected = state(machine).getParallelsBig()
            .multiply(BigInteger.valueOf(4096));
        check(
            expected.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0,
            "native fixture did not reach chunk boundary");
        check(
            machine.onRunningTick(null) && initial.subtract(machine.costingEU)
                .equals(WirelessNetworkManager.getUserEU(owner)),
            "native batch charged twice");
        WirelessControllerEnergy.complete(machine);
        check(
            output.getProvider()
                .getCachedAmountBig()
                .equals(expected),
            "native wireless output lost quantity");
    }

    private static void verifyNativeControls(int tier, int count, int controlCount, boolean batchMode) {
        verifyNativeControls(tier, count, controlCount, batchMode, false);
    }

    private static void verifyNativeControls(int tier, int count, int controlCount, boolean batchMode,
        boolean exactOutput) {
        try {
            UUID owner = UUID.randomUUID();
            BigInteger initial = BigInteger.TEN.pow(60);
            WirelessNetworkManager.addEUToGlobalEnergyMap(owner, initial);
            RealCrusher machine = new RealCrusher(owner);
            com.science.gtnl.common.machine.hatch.ParallelControllerHatch control = new com.science.gtnl.common.machine.hatch.ParallelControllerHatch(
                "apeiron.verify.gtnl.native_control",
                tier,
                0,
                new String[4],
                null);
            control.setBaseMetaTileEntity(new BaseMetaTileEntity());
            control.parallel = controlCount == 0 ? control.maxParallel : controlCount;
            machine.mParallelControllerHatches.add(control);
            java.lang.reflect.Field useMax = MTEMultiBlockBase.class.getDeclaredField("alwaysMaxParallel");
            java.lang.reflect.Field setting = MTEMultiBlockBase.class.getDeclaredField("powerPanelMaxParallel");
            useMax.setAccessible(true);
            setting.setAccessible(true);
            useMax.setBoolean(machine, false);
            setting.setInt(machine, count);
            machine.setBatchMode(batchMode);
            MTEHatchInputBus input = new MTEHatchInputBus(
                "apeiron.verify.gtnl.native_controls_input",
                1,
                new String[0],
                null);
            input.setBaseMetaTileEntity(new BaseMetaTileEntity());
            input.setInventorySlotContents(0, new ItemStack(Items.diamond));
            machine.mInputBusses.add(input);
            MTEInfiniteMEOutputAssembly mixed = exactOutput ? output(machine, owner) : null;
            gregtech.api.metatileentity.implementations.MTEHatchOutputBus output = exactOutput ? null
                : new gregtech.api.metatileentity.implementations.MTEHatchOutputBus(
                    "apeiron.verify.gtnl.native_controls_output",
                    1,
                    new String[0],
                    null);
            if (output != null) {
                output.setBaseMetaTileEntity(new BaseMetaTileEntity());
                machine.mOutputBusses.add(output);
            }
            GTRecipe probe = recipe(new ItemStack(Items.diamond, 0), new ItemStack(Items.emerald, 2), 20, 30, false);
            machine.recipes.addRecipe(probe);
            machine.wirelessUpgrade = true;
            machine.setWirelessMode(true);
            machine.resetParallelTier();
            int base = machine.getTrueParallel();
            com.science.gtnl.utils.recipes.GTNLProcessingLogic original = (com.science.gtnl.utils.recipes.GTNLProcessingLogic) machine
                .createProcessingLogic();
            machine.setupProcessingLogic(original);
            GTNLOverclockCalculator originalOC = original.createOverclockCalculator(probe);
            // A direct helper call bypasses Apeiron's processing redirect and executes GTNL's own implementation.
            com.science.gtnl.utils.recipes.GTNLParallelHelper originalHelper = original.createParallelHelper(probe)
                .setMaxParallel(base)
                .setItemInputs(new ItemStack[] { new ItemStack(Items.diamond) })
                .setFluidInputs(new FluidStack[0])
                .setConsumption(false)
                .setOutputCalculation(false)
                .setCalculator(originalOC)
                .build();
            check(
                originalHelper.getResult()
                    .wasSuccessful(),
                "original GTNL control comparison failed");
            BigInteger expected = BigInteger.valueOf(originalHelper.getCurrentParallel());
            check(
                machine.checkProcessing()
                    .wasSuccessful(),
                "native controls rejected recipe");
            check(
                state(machine).getParallelsBig()
                    .equals(expected),
                "native tier " + tier
                    + " base "
                    + base
                    + " expected original "
                    + expected
                    + " but executed "
                    + state(machine).getParallelsBig());
            GtnlOverclocks exact = GtnlOverclocks.calculate(originalOC, expected.min(BigInteger.valueOf(base)));
            check(
                machine.costingEU.equals(initial.subtract(WirelessNetworkManager.getUserEU(owner)))
                    && machine.costingEU.equals(exact.eut.multiply(BigInteger.valueOf(machine.mMaxProgresstime))),
                "native control cost no longer follows original overclocks or exact account debit");
            WirelessControllerEnergy.complete(machine);
            BigInteger produced = mixed == null ? BigInteger.ZERO
                : mixed.getProvider()
                    .getCachedAmountBig();
            if (output != null) for (int slot = 0; slot < output.getSizeInventory(); slot++) {
                ItemStack item = output.getStackInSlot(slot);
                if (item != null) produced = produced.add(BigInteger.valueOf(item.stackSize));
            }
            check(
                produced.equals(expected.multiply(BigInteger.valueOf(2))),
                "native-only output differs from original GTNL calculation");
            Apeiron.LOG.info(
                "GTNL native control comparison passed: tier={}, hatch={}, machine={}, batch={}, base={}, actual={}, exactOutput={}",
                tier,
                control.parallel,
                count,
                batchMode,
                base,
                expected,
                exactOutput);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("GTNL real native controls verification failed", error);
        }
    }

    private static void verifyGeneric(boolean ultimate) {
        UUID owner = UUID.randomUUID();
        BigInteger initial = BigInteger.TEN.pow(60);
        WirelessNetworkManager.addEUToGlobalEnergyMap(owner, initial);
        Furnace machine = new Furnace(owner);
        energy(machine, owner, ultimate);
        MTEInfiniteMEOutputAssembly output = output(machine, owner);
        MTEInfinitePatternInputAssembly input = (MTEInfinitePatternInputAssembly) tile(
            ApeironMachines.PATTERN_INPUT_ASSEMBLY_OFFSET,
            owner).getMetaTileEntity();
        check(
            input.addToBufferBig(
                0,
                Arrays.asList(BigAEStackValues.copyWithSize(AEItemStack.create(new ItemStack(Items.diamond)), HUGE))),
            "GTNL pattern input setup failed");
        check(
            input.addToBufferBig(
                1,
                Arrays.asList(
                    BigAEStackValues
                        .copyWithSize(AEItemStack.create(new ItemStack(Items.iron_ingot)), BigInteger.valueOf(13)))),
            "GTNL isolated second recipe setup failed");
        machine.mDualInputHatches.add(input);
        machine.recipes.addRecipe(recipe(new ItemStack(Items.diamond), new ItemStack(Items.emerald, 2), 20, 32, true));
        machine.recipes.addRecipe(recipe(new ItemStack(Items.iron_ingot), new ItemStack(Items.apple, 3), 40, 64, true));
        state(machine).setParallelSettingBig(ultimate ? HUGE.add(BigInteger.valueOf(13)) : BigInteger.valueOf(37));
        state(machine).setVoltageSetting(32);
        state(machine).setTargetDuration(7);
        machine.setWirelessMode(false);
        input.beginRecipeProcessing();
        CheckRecipeResult result;
        try {
            result = machine.checkProcessing();
        } finally {
            input.endRecipeProcessing();
        }
        check(result.wasSuccessful(), "GTNL generic big processing failed: " + result.getID());
        BigInteger expected = ultimate ? HUGE.add(BigInteger.valueOf(13)) : BigInteger.valueOf(37);
        check(
            state(machine).getParallelsBig()
                .equals(expected),
            "generic parallel or cross-recipe limit ignored");
        int ticks = machine.mMaxProgresstime;
        check(!ultimate || ticks == 7, "generic duration setting ignored");
        for (int tick = 0; tick < ticks; tick++)
            check(WirelessControllerEnergy.debitTick(machine), "generic tick energy debit failed");
        BigInteger used = state(machine).getTotalEUBig();
        check(
            initial.subtract(used)
                .equals(WirelessNetworkManager.getUserEU(owner)),
            "generic exact EU mismatch");
        WirelessControllerEnergy.complete(machine);
        check(
            output.getFluidProvider()
                .getCachedAmountBig()
                .equals(expected.multiply(BigInteger.valueOf(1000))),
            "generic big fluid output truncated");
        check(
            output.getProvider()
                .getCachedAmountBig()
                .equals(
                    ultimate ? HUGE.multiply(BigInteger.valueOf(2))
                        .add(BigInteger.valueOf(39)) : BigInteger.valueOf(74)),
            "generic big item output truncated");
    }

    private static void verifyMissingOutput() {
        UUID owner = UUID.randomUUID();
        BigInteger initial = BigInteger.TEN.pow(20);
        WirelessNetworkManager.addEUToGlobalEnergyMap(owner, initial);
        Furnace machine = new Furnace(owner);
        energy(machine, owner, true);
        MTEHatchInputBus input = new MTEHatchInputBus("apeiron.verify.gtnl.missing_output", 1, new String[0], null);
        input.setBaseMetaTileEntity(new BaseMetaTileEntity());
        input.setInventorySlotContents(0, new ItemStack(Items.diamond, 37));
        machine.mInputBusses.add(input);
        machine.recipes.addRecipe(recipe(new ItemStack(Items.diamond), new ItemStack(Items.emerald), 20, 32, false));
        CheckRecipeResult result = machine.checkProcessing();
        check(
            !result.wasSuccessful() && input.getStackInSlot(0).stackSize == 37
                && WirelessNetworkManager.getUserEU(owner)
                    .equals(initial),
            "missing output consumed inputs or energy");
    }

    private static void verifyUnmodifiedMachine() {
        UUID owner = UUID.randomUUID();
        Crusher machine = new Crusher(owner);
        machine.setWirelessMode(true);
        check(!machine.wirelessMode && !state(machine).isRunning(), "ordinary machine forced into wireless mode");
        NBTTagCompound saved = new NBTTagCompound();
        machine.setItemNBT(saved);
        check(
            !saved.hasKey("ApeironWirelessRecipe") && !saved.getBoolean("wirelessUpgrade"),
            "ordinary controller acquired upgrade NBT");
    }

    private static void verifyLowEnergy() {
        UUID owner = UUID.randomUUID();
        BigInteger initial = BigInteger.valueOf(639);
        WirelessNetworkManager.addEUToGlobalEnergyMap(owner, initial);
        Furnace machine = new Furnace(owner);
        energy(machine, owner, true);
        output(machine, owner);
        MTEHatchInputBus input = new MTEHatchInputBus("apeiron.verify.gtnl.budget", 1, new String[0], null);
        input.setBaseMetaTileEntity(new BaseMetaTileEntity());
        input.setInventorySlotContents(0, new ItemStack(Items.diamond, 37));
        machine.mInputBusses.add(input);
        machine.recipes.addRecipe(recipe(new ItemStack(Items.diamond), new ItemStack(Items.emerald), 20, 32, false));
        check(
            !machine.checkProcessing()
                .wasSuccessful() && input.getStackInSlot(0).stackSize == 37
                && WirelessNetworkManager.getUserEU(owner)
                    .equals(initial)
                && !state(machine).isRunning(),
            "unaffordable recipe debited input or energy");
    }

    private static void verifyNativeWired() {
        UUID owner = UUID.randomUUID();
        Furnace machine = new Furnace(owner);
        machine.limit = 1_000_000;
        machine.setBatchMode(false);
        gregtech.api.metatileentity.implementations.MTEHatchEnergy power = new gregtech.api.metatileentity.implementations.MTEHatchEnergy(
            "apeiron.verify.gtnl.wired",
            1,
            new String[0],
            null);
        power.setBaseMetaTileEntity(new BaseMetaTileEntity());
        machine.mEnergyHatches.add(power);
        output(machine, owner);
        MTEHatchInputBus input = new MTEHatchInputBus("apeiron.verify.gtnl.wired_inputs", 1, new String[0], null);
        input.setBaseMetaTileEntity(new BaseMetaTileEntity());
        input.setInventorySlotContents(0, new ItemStack(Items.diamond, 37));
        machine.mInputBusses.add(input);
        machine.recipes.addRecipe(recipe(new ItemStack(Items.diamond), new ItemStack(Items.emerald), 20, 8, false));
        CheckRecipeResult result = machine.checkProcessing();
        check(result.wasSuccessful(), "native wired GTNL recipe failed: " + result.getID());
        check(
            state(machine).getParallelsBig()
                .compareTo(BigInteger.valueOf(37)) < 0
                && state(machine).getEUtBig()
                    .compareTo(BigInteger.valueOf(machine.getMaxInputEu())) <= 0
                && input.getStackInSlot(0).stackSize == 37 - state(machine).getParallelsBig()
                    .intValueExact(),
            "wired power budget did not constrain the input debit");
        check(
            state(machine).usesNativeEnergy() && !machine.wirelessMode && !machine.onRunningTick(null),
            "exact output bypassed native wired energy");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
