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
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigPlasmaMixerController;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.me.output.verification.InfiniteMEOutputAssemblySmoke;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;
import com.silvia.apeiron.config.MixinFeature;

import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTRecipeConstants;
import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;
import gregtech.common.tileentities.machines.multi.MTETranscendentPlasmaMixer;

/** Real native lookup/startup/completion, exact prepaid cost, old saves and the disabled bootstrap. */
public final class PlasmaMixerSmoke {

    private static int maps;
    private static final BigInteger HUGE = BigInteger.TEN.pow(40)
        .add(BigInteger.valueOf(17));
    private static final BigInteger UNIT = BigInteger.valueOf(6000);

    private PlasmaMixerSmoke() {}

    private static final class Controller extends MTETranscendentPlasmaMixer {

        private final RecipeMap<?> recipes;

        Controller(UUID owner, GTRecipe recipe) throws ReflectiveOperationException {
            super("apeiron.verify.plasma_mixer");
            setBaseMetaTileEntity(new BaseMetaTileEntity());
            java.lang.reflect.Field field = MTETranscendentPlasmaMixer.class.getDeclaredField("ownerUUID");
            field.setAccessible(true);
            field.set(this, owner);
            recipes = recipe == null ? null
                : RecipeMapBuilder.of("apeiron.verify.plasma_mixer." + maps++)
                    .maxIO(3, 1, 24, 1)
                    .build();
            if (recipes != null) recipes.addRecipe(recipe);
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return recipes == null ? super.getRecipeMap() : recipes;
        }

        void complete() {
            outputAfterRecipe();
        }
    }

    private static final class Fixture {

        final UUID owner = UUID.randomUUID();
        final Controller machine;
        final MTEInfinitePatternInputAssembly input;
        final MTEInfiniteMEOutputAssembly output = InfiniteMEOutputAssemblySmoke.assembly();

        Fixture(GTRecipe recipe, BigInteger count, boolean storedRecipe) throws ReflectiveOperationException {
            machine = new Controller(owner, storedRecipe ? null : recipe);
            ApeironMachineTile tile = new ApeironMachineTile();
            tile.setInitialValuesAsNBT(
                null,
                (short) ApeironConfig.getMachineId(ApeironMachines.PATTERN_INPUT_ASSEMBLY_OFFSET));
            input = (MTEInfinitePatternInputAssembly) tile.getMetaTileEntity();
            check(machine.addToMachineList(tile, 0), "pattern input registration");
            check(machine.addToMachineList(output.getBaseMetaTileEntity(), 0), "ME output registration");
            List<IAEStack<?>> stocks = new ArrayList<>();
            for (ItemStack item : recipe.mInputs) {
                if (item == null) continue;
                ItemStack type = item.copy();
                type.stackSize = 1;
                stocks.add(
                    BigAEStackValues.copyWithSize(
                        AEItemStack.create(type),
                        item.stackSize == 0 ? BigInteger.ONE : count.multiply(BigInteger.valueOf(item.stackSize))));
            }
            for (FluidStack fluid : recipe.mFluidInputs) stocks.add(
                BigAEStackValues
                    .copyWithSize(AEFluidStack.create(fluid), count.multiply(BigInteger.valueOf(fluid.amount))));
            check(input.addToBufferBig(0, stocks), "input fixture");
        }

        BigPlasmaMixerController settings() {
            return (BigPlasmaMixerController) (Object) machine;
        }

        WirelessRecipeState state() {
            return ((BigWirelessController) (Object) machine).getWirelessRecipeState();
        }

        CheckRecipeResult attempt() {
            input.beginRecipeProcessing();
            try {
                return machine.checkProcessing();
            } finally {
                input.endRecipeProcessing();
            }
        }

        void finish() {
            BigInteger balance = WirelessNetworkManager.getUserEU(owner);
            check(machine.onRunningTick(null), "prepaid running tick failed");
            check(
                WirelessNetworkManager.getUserEU(owner)
                    .equals(balance),
                "prepaid batch debited twice");
            machine.complete();
            machine.complete();
            check(
                !state().isRunning() && state().pending()
                    .isEmpty(),
                "completed output remained pending");
        }
    }

    public static void verify() {
        HashMap<UUID, BigInteger> energy = GlobalVariableStorage.GlobalEnergy;
        Map<UUID, UUID> teams = SpaceProjectManager.spaceTeams;
        GlobalEnergyWorldSavedData save = GlobalEnergyWorldSavedData.INSTANCE;
        try {
            GlobalVariableStorage.GlobalEnergy = new HashMap<>();
            SpaceProjectManager.spaceTeams = new HashMap<>();
            GlobalEnergyWorldSavedData.INSTANCE = new GlobalEnergyWorldSavedData();
            if (!MixinFeature.PLASMA_MIXER.isEnabled()) {
                verifyDisabled();
                return;
            }
            Controller fresh = new Controller(UUID.randomUUID(), recipe());
            BigPlasmaMixerController controls = (BigPlasmaMixerController) (Object) fresh;
            check(
                controls.getPlasmaMixerParallel()
                    .equals(BigInteger.valueOf(Integer.MAX_VALUE)) && controls.getPlasmaMixerDuration() == 1,
                "wrong default controls");
            for (BigInteger limit : new BigInteger[] { HUGE, BigInteger.ZERO }) verifyHuge(limit);
            verifyBudget();
            verifyOutputFull();
            verifyOutputLimit();
            verifyChangedBalance();
            verifyOverflow();
            verifyReload();
            verifyRegisteredRecipe();
            if (cpw.mods.fml.common.FMLCommonHandler.instance()
                .getSide()
                .isClient()) PlasmaMixerPanelSmoke.verify();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Plasma mixer fixture failed", error);
        } finally {
            GlobalVariableStorage.GlobalEnergy = energy;
            SpaceProjectManager.spaceTeams = teams;
            GlobalEnergyWorldSavedData.INSTANCE = save;
        }
        Apeiron.LOG.info(
            "Plasma mixer verification passed: 1-tick/default cap, >long/zero parallel, original prepaid energy/metadata, exact inputs/outputs, budget, capacity, reload and real GT recipe");
    }

    private static GTRecipe recipe() {
        return GTRecipeBuilder.builder()
            .itemInputs(new ItemStack(Items.diamond, 2))
            .fluidInputs(new FluidStack(FluidRegistry.WATER, 2))
            .itemOutputs(new ItemStack(Items.emerald, 3))
            .fluidOutputs(new FluidStack(FluidRegistry.LAVA, 1000))
            .eut(6)
            .duration(100)
            .build()
            .get();
    }

    private static void verifyHuge(BigInteger limit) throws ReflectiveOperationException {
        Fixture f = new Fixture(recipe(), HUGE, false);
        f.settings()
            .setPlasmaMixerParallel(limit);
        WirelessNetworkManager.setUserEU(f.owner, UNIT.multiply(HUGE));
        check(
            f.attempt()
                .wasSuccessful(),
            "huge recipe rejected");
        check(
            f.machine.mMaxProgresstime == 1 && f.state()
                .getParallelsBig()
                .equals(HUGE),
            "time/parallel truncated");
        check(
            f.state()
                .usesNativeEnergy()
                && f.state()
                    .getTotalEUBig()
                    .equals(UNIT.multiply(HUGE)),
            "prepaid cost changed");
        check(
            WirelessNetworkManager.getUserEU(f.owner)
                .signum() == 0 && f.input.getBuffers()
                    .get(0)
                    .isEmpty(),
            "energy/input conservation");
        f.finish();
        check(
            f.output.getProvider()
                .getCachedAmountBig()
                .equals(HUGE.multiply(BigInteger.valueOf(3))),
            "item output truncated");
        check(
            f.output.getFluidProvider()
                .getCachedAmountBig()
                .equals(HUGE.multiply(BigInteger.valueOf(1000))),
            "fluid output truncated");
    }

    private static void verifyBudget() throws ReflectiveOperationException {
        Fixture f = new Fixture(recipe(), BigInteger.valueOf(5), false);
        f.settings()
            .setPlasmaMixerParallel(BigInteger.ZERO);
        f.settings()
            .setPlasmaMixerDuration(7);
        WirelessNetworkManager.setUserEU(f.owner, UNIT.multiply(BigInteger.valueOf(3)));
        check(
            f.attempt()
                .wasSuccessful()
                && f.state()
                    .getParallelsBig()
                    .intValue() == 3
                && f.machine.mMaxProgresstime == 7,
            "finite budget was not respected");
        check(
            f.input.getBuffers()
                .get(0)
                .getItemAmountBig()
                .intValue() == 4
                && f.input.getBuffers()
                    .get(0)
                    .getFluidAmountBig()
                    .intValue() == 4,
            "limited input debit changed");
        f.finish();
        check(
            !f.attempt()
                .wasSuccessful()
                && f.input.getBuffers()
                    .get(0)
                    .getItemAmountBig()
                    .intValue() == 4,
            "unfunded batch consumed input");
    }

    private static void verifyOutputFull() throws ReflectiveOperationException {
        Fixture f = new Fixture(recipe(), BigInteger.ONE, false);
        f.machine.mOutputBusses.clear();
        f.machine.mOutputHatches.clear();
        WirelessNetworkManager.setUserEU(f.owner, UNIT);
        check(
            !f.attempt()
                .wasSuccessful() && WirelessNetworkManager.getUserEU(f.owner)
                    .equals(UNIT)
                && f.input.getBuffers()
                    .get(0)
                    .getItemAmountBig()
                    .intValue() == 2,
            "output-full batch consumed resources");
    }

    private static void verifyOverflow() throws ReflectiveOperationException {
        GTRecipe recipe = GTRecipeBuilder.builder()
            .itemInputs(new ItemStack(Items.diamond, 2))
            .fluidInputs(new FluidStack(FluidRegistry.WATER, 2))
            .itemOutputs(new ItemStack(Items.emerald, 3))
            .fluidOutputs(new FluidStack(FluidRegistry.LAVA, 1000))
            .eut(Integer.MAX_VALUE)
            .duration(Integer.MAX_VALUE)
            .metadata(GTRecipeConstants.EU_MULTIPLIER, Integer.MAX_VALUE)
            .build()
            .get();
        Fixture f = new Fixture(recipe, BigInteger.ONE, false);
        BigInteger cost = BigInteger.valueOf(Integer.MAX_VALUE)
            .pow(3);
        WirelessNetworkManager.setUserEU(f.owner, cost);
        check(
            f.attempt()
                .wasSuccessful()
                && f.state()
                    .getTotalEUBig()
                    .equals(cost)
                && WirelessNetworkManager.getUserEU(f.owner)
                    .signum() == 0,
            "metadata energy overflowed");
        f.finish();
    }

    private static void verifyOutputLimit() throws ReflectiveOperationException {
        Fixture f = new Fixture(recipe(), BigInteger.valueOf(5), false);
        f.machine.mOutputHatches.clear();
        gregtech.api.metatileentity.implementations.MTEHatchOutput tank = new gregtech.api.metatileentity.implementations.MTEHatchOutput(
            "apeiron.verify.plasma_output",
            0,
            new String[0],
            null);
        tank.setBaseMetaTileEntity(new BaseMetaTileEntity());
        tank.mFluid = new FluidStack(FluidRegistry.LAVA, tank.getCapacity() - 3000);
        f.machine.mOutputHatches.add(tank);
        WirelessNetworkManager.setUserEU(f.owner, UNIT.multiply(BigInteger.valueOf(5)));
        check(
            f.attempt()
                .wasSuccessful()
                && f.state()
                    .getParallelsBig()
                    .intValue() == 3,
            "physical output did not bound parallel");
        f.finish();
        check(
            tank.mFluid.amount == tank.getCapacity() && WirelessNetworkManager.getUserEU(f.owner)
                .equals(UNIT.multiply(BigInteger.valueOf(2))),
            "physical output or energy conservation changed");
    }

    private static void verifyChangedBalance() throws ReflectiveOperationException {
        Fixture f = new Fixture(recipe(), BigInteger.valueOf(2), false);
        WirelessNetworkManager.setUserEU(f.owner, UNIT.multiply(BigInteger.valueOf(2)));
        f.input.beginRecipeProcessing();
        try {
            com.silvia.apeiron.common.machine.parallel.BigPrepaidParallelHelper helper = new com.silvia.apeiron.common.machine.parallel.BigPrepaidParallelHelper(
                f.machine,
                f.owner,
                com.silvia.apeiron.api.machine.parallel.ParallelLimit.bounded(2),
                UNIT,
                new gregtech.api.util.ParallelHelper());
            helper.setRecipe(recipe())
                .setItemInputs(
                    f.input.getBuffers()
                        .get(0)
                        .getItemInputs())
                .setFluidInputs(
                    f.input.getBuffers()
                        .get(0)
                        .getFluidInputs())
                .setCalculator(gregtech.api.util.OverclockCalculator.ofNoOverclock(recipe()))
                .setOutputCalculation(true)
                .setConsumption(true)
                .build();
            check(
                helper.getResult()
                    .wasSuccessful(),
                "account-change plan failed");
            WirelessNetworkManager.setUserEU(f.owner, BigInteger.ZERO);
            check(
                !helper.start(1)
                    .wasSuccessful()
                    && !f.state()
                        .isRunning(),
                "changed account incorrectly started batch");
        } finally {
            f.input.endRecipeProcessing();
        }
        check(
            f.input.getBuffers()
                .get(0)
                .getItemAmountBig()
                .intValue() == 4
                && f.input.getBuffers()
                    .get(0)
                    .getFluidAmountBig()
                    .intValue() == 4,
            "failed atomic debit consumed input");
    }

    private static void verifyReload() throws ReflectiveOperationException {
        Fixture f = new Fixture(recipe(), HUGE, false);
        f.settings()
            .setPlasmaMixerParallel(HUGE);
        f.settings()
            .setPlasmaMixerDuration(9);
        WirelessNetworkManager.setUserEU(f.owner, UNIT.multiply(HUGE));
        check(
            f.attempt()
                .wasSuccessful(),
            "reload recipe rejected");
        NBTTagCompound saved = new NBTTagCompound();
        f.machine.saveNBTData(saved);
        Fixture restored = new Fixture(recipe(), BigInteger.ONE, false);
        restored.machine.loadNBTData(saved);
        check(
            restored.settings()
                .getPlasmaMixerParallel()
                .equals(HUGE)
                && restored.settings()
                    .getPlasmaMixerDuration() == 9
                && restored.state()
                    .usesNativeEnergy()
                && restored.state()
                    .getTotalEUBig()
                    .equals(UNIT.multiply(HUGE)),
            "reload lost controls or prepaid ledger");
        restored.finish();
        check(
            restored.output.getFluidProvider()
                .getCachedAmountBig()
                .equals(HUGE.multiply(BigInteger.valueOf(1000))),
            "reload lost output");
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setInteger("eMultiplier", 5);
        restored.machine.loadNBTData(legacy);
        check(
            restored.settings()
                .getPlasmaMixerParallel()
                .intValue() == 5
                && restored.settings()
                    .getPlasmaMixerDuration() == 1,
            "legacy parallel migration changed selection");
    }

    private static void verifyRegisteredRecipe() throws ReflectiveOperationException {
        Controller lookup = new Controller(UUID.randomUUID(), null);
        GTRecipe recipe = lookup.getRecipeMap()
            .getAllRecipes()
            .stream()
            .filter(r -> r.mFluidInputs.length > 0)
            .findFirst()
            .orElse(null);
        if (recipe == null) {
            Apeiron.LOG.info(
                "Native plasma mixer recipes are absent in the minimal compatibility profile; real controller transaction fixtures passed");
            return;
        }
        Fixture f = new Fixture(recipe, HUGE, true);
        f.settings()
            .setPlasmaMixerParallel(BigInteger.ZERO);
        BigInteger cost = BigInteger.valueOf(recipe.mEUt)
            .multiply(BigInteger.valueOf(recipe.mDuration))
            .multiply(BigInteger.valueOf(recipe.getMetadataOrDefault(GTRecipeConstants.EU_MULTIPLIER, 10)))
            .multiply(HUGE);
        WirelessNetworkManager.setUserEU(f.owner, cost);
        check(
            f.attempt()
                .wasSuccessful()
                && f.state()
                    .getParallelsBig()
                    .equals(HUGE),
            "real GT recipe rejected");
        check(
            f.input.getBuffers()
                .get(0)
                .getFluidAmountBig()
                .signum() == 0,
            "real GT fluids remained");
        f.finish();
        check(
            f.output.getFluidProvider()
                .getCachedAmountBig()
                .equals(HUGE.multiply(BigInteger.valueOf(recipe.mFluidOutputs[0].amount))),
            "real GT products truncated");
        Apeiron.LOG.info(
            "Registered plasma mixer recipe verification passed: native recipe lookup, catalysts, exact fluids/output and original metadata energy");
    }

    private static void verifyDisabled() throws ReflectiveOperationException {
        Fixture f = new Fixture(recipe(), BigInteger.valueOf(2), false);
        check(
            !((Object) f.machine instanceof BigPlasmaMixerController) && f.machine.getMultiplier() == 1,
            "disabled integration remained active");
        f.machine.setMultiplier(2);
        WirelessNetworkManager.setUserEU(f.owner, UNIT.multiply(BigInteger.valueOf(2)));
        check(
            f.attempt()
                .wasSuccessful() && f.machine.mMaxProgresstime == 100
                && !f.state()
                    .isRunning(),
            "disabled native behavior changed");
        check(
            WirelessNetworkManager.getUserEU(f.owner)
                .signum() == 0,
            "disabled native energy changed");
        Apeiron.LOG
            .info("Disabled plasma mixer verification passed: native controls, time and wireless debit restored");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException("Plasma mixer: " + message);
    }
}
