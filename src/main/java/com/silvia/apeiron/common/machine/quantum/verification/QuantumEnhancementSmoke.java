package com.silvia.apeiron.common.machine.quantum.verification;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.api.machine.quantum.EnhancedQuantumController;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.input.storage.BigPatternBuffer;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.me.output.verification.InfiniteMEOutputAssemblySmoke;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.common.machine.quantum.MTEQuantumEnhancementModule;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;
import com.silvia.apeiron.mixin.gregtech.output.MultiBlockProcessingAccessor;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import cpw.mods.fml.common.Loader;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchBulkCatalystHousing;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTRecipeConstants;
import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;
import gtPlusPlus.xmod.gregtech.api.enums.GregtechItemList;
import gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.production.MTEQuantumForceTransformer;

/** Exercise real QFT validation, isolated pattern inputs and the common exact processing/debit/completion path. */
public final class QuantumEnhancementSmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(30)
        .add(BigInteger.valueOf(19));
    private static int recipeMapId;

    private QuantumEnhancementSmoke() {}

    private static final class Fixture extends MTEQuantumForceTransformer {

        private RecipeMap<?> recipes;

        Fixture(GTRecipe recipe) {
            super("apeiron.verify.quantum");
            setBaseMetaTileEntity(new BaseMetaTileEntity());
            mCraftingTier = 4;
            mFocusingTier = 4;
            mWrench = mScrewdriver = mSoftMallet = mHardHammer = mSolderingTool = mCrowbar = true;
            recipes = RecipeMapBuilder.of("apeiron.verify.quantum." + recipeMapId++)
                .maxIO(1, 2, 1, 1)
                .build();
            recipes.addRecipe(recipe);
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return recipes;
        }

        @Override
        protected void sendStartMultiBlockSoundLoop() {}

        void complete() {
            outputAfterRecipe();
        }
    }

    private static final class Source extends MTEInfinitePatternInputAssembly {

        Source() {
            super("apeiron.verify.quantum_source", 10, new String[0], null);
            setBaseMetaTileEntity(tile(ApeironMachines.PATTERN_INPUT_ASSEMBLY_OFFSET));
        }

        @Override
        public boolean isActive() {
            return true;
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
            verifyNative();
            verifyExact(false, false);
            verifyExact(true, false);
            verifyExact(true, true);
            verifyIsolationAndCapacity();
            verifyDrop();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Quantum enhancement verification failed", error);
        } finally {
            GlobalVariableStorage.GlobalEnergy = energy;
            SpaceProjectManager.spaceTeams = teams;
            GlobalEnergyWorldSavedData.INSTANCE = save;
        }
        Apeiron.LOG.info(
            "Quantum enhancement verification passed: native catalyst/chance preserved, isolated programming selectors, exact parallel/time/energy, fluid mode, capacity and persistence");
    }

    private static ItemStack catalyst() {
        return GregtechItemList.PlatinumGroupCatalyst.get(1);
    }

    private static GTRecipe recipe() {
        return GTRecipeBuilder.builder()
            .itemInputs(new ItemStack(Items.diamond, 2))
            .itemOutputs(new ItemStack(Items.emerald, 3), GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Iron, 2))
            .outputChances(2500, 1000)
            .fluidInputs(new FluidStack(FluidRegistry.WATER, 1))
            .fluidOutputs(new FluidStack(FluidRegistry.WATER, 5))
            .duration(20)
            .eut(8)
            .specialValue(1)
            .metadata(GTRecipeConstants.QFT_CATALYST, catalyst())
            .build()
            .get();
    }

    private static void verifyNative() throws ReflectiveOperationException {
        GTRecipe recipe = recipe();
        Fixture controller = new Fixture(recipe);
        ProcessingLogic logic = ((MultiBlockProcessingAccessor) (Object) controller).apeiron$getProcessingLogic();
        logic.setMachine(controller)
            .setInputItems(catalyst())
            .setInputFluids();
        check(!validate(logic, recipe).wasSuccessful(), "native validation accepted a marker without a housing");
        MTEHatchBulkCatalystHousing housing = new MTEHatchBulkCatalystHousing(
            "apeiron.verify.quantum_catalyst",
            new String[0],
            null,
            10,
            64);
        housing.setBaseMetaTileEntity(new BaseMetaTileEntity());
        housing.setItemStack(catalyst());
        housing.setItemCount(7);
        check(
            controller.addCatalystHousingToMachineList(housing.getBaseMetaTileEntity(), 0),
            "native housing rejected");
        check(validate(logic, recipe).wasSuccessful(), "native catalyst stopped working");
        check((Integer) field(controller, "mMaxParallel") == 7, "native catalyst parallel count changed");
        int[] chances = (int[]) field(logic, "chances");
        check(
            chances.length == 3 && chances[0] == 3333 && chances[1] == 3333,
            "native probabilities were guaranteed without module");
        check(
            !((EnhancedQuantumController) (Object) controller).hasQuantumEnhancement(),
            "ordinary housing enabled enhancement");
    }

    private static MTEInfiniteEnergyHatch attach(Fixture controller, boolean ultimate) {
        ApeironMachineTile energy = tile(
            ultimate ? ApeironMachines.ULTIMATE_ENERGY_HATCH_OFFSET : ApeironMachines.INFINITE_ENERGY_HATCH_OFFSET);
        UUID owner = UUID.randomUUID();
        energy.setOwnerUuid(owner);
        WirelessNetworkManager.setUserEU(owner, BigInteger.TEN.pow(100));
        MTEInfiniteEnergyHatch hatch = (MTEInfiniteEnergyHatch) energy.getMetaTileEntity();
        controller.mEnergyHatches.add(hatch);
        ApeironMachineTile module = tile(ApeironMachines.QUANTUM_ENHANCEMENT_OFFSET);
        check(
            controller.addCatalystHousingToMachineList(module, 0),
            "bottom catalyst structure element rejected module");
        check(
            ((EnhancedQuantumController) (Object) controller).hasQuantumEnhancement(),
            "structure did not enable module");
        return hatch;
    }

    private static Source source(Fixture controller, ItemStack selector, BigInteger batches) {
        Source source = new Source();
        BigPatternBuffer buffer = source.getBuffers()
            .get(0);
        buffer.assign(0, null, Collections.singletonList(selector));
        buffer.add(inputs(batches), BigInteger.ONE);
        controller.mDualInputHatches.add(source);
        return source;
    }

    private static ArrayList<IAEStack<?>> inputs(BigInteger batches) {
        return new ArrayList<>(
            Arrays.asList(
                BigAEStackValues.copyWithSize(
                    AEItemStack.create(new ItemStack(Items.diamond)),
                    batches.multiply(BigInteger.valueOf(2))),
                BigAEStackValues.copyWithSize(AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)), batches)));
    }

    private static MTEInfiniteMEOutputAssembly output(Fixture controller) {
        MTEInfiniteMEOutputAssembly output = InfiniteMEOutputAssemblySmoke.assembly();
        output.getProvider()
            .setCheckMode(true);
        output.getFluidProvider()
            .setCheckMode(true);
        controller.addOutputBusToMachineList(output.getBaseMetaTileEntity(), 0);
        return output;
    }

    private static void verifyExact(boolean ultimate, boolean fluidMode) throws ReflectiveOperationException {
        GTRecipe recipe = recipe();
        Fixture controller = new Fixture(recipe);
        MTEInfiniteEnergyHatch hatch = attach(controller, ultimate);
        MTEInfiniteMEOutputAssembly output = output(controller);
        set(controller, "mFluidMode", fluidMode);
        Source source = source(controller, catalyst(), HUGE);
        if (Loader.isModLoaded("programmablehatches")) verifyProgrammedInput(source);
        WirelessRecipeState state = ((BigWirelessController) (Object) controller).getWirelessRecipeState();
        state.setParallelSettingBig(HUGE);
        state.setVoltageSetting(8);
        state.setTargetDuration(1);
        BigInteger before = hatch.getAvailableEUBig();
        check(
            ((MultiBlockProcessingAccessor) (Object) controller).apeiron$checkRecipe(),
            "enhanced recipe did not start");
        check(
            state.isRunning() && state.getParallelsBig()
                .equals(HUGE),
            "enhanced parallel setting was ignored");
        check(controller.mMaxProgresstime == (ultimate ? 1 : 20), "voltage or completion time ignored");
        check(
            source.getBuffers()
                .get(0)
                .isEmpty(),
            "exact inputs not consumed");
        check(
            state.getTotalEUBig()
                .equals(HUGE.multiply(BigInteger.valueOf(160))),
            "recipe energy changed");
        check(
            !(Boolean) field(controller, "doFermium") && !(Boolean) field(controller, "doNeptunium"),
            "guaranteed outputs still need focus plasma");
        check(recipe.getOutputChance(0) == 2500 && recipe.getOutputChance(1) == 1000, "shared recipe was mutated");
        NBTTagCompound saved = new NBTTagCompound();
        controller.saveNBTData(saved);
        Fixture restored = new Fixture(recipe);
        restored.loadNBTData(saved);
        WirelessRecipeState restoredState = ((BigWirelessController) (Object) restored).getWirelessRecipeState();
        check(
            restoredState.getParallelsBig()
                .equals(HUGE) && restoredState.isRunning(),
            "save lost enhanced recipe");
        WirelessNetworkManager.setUserEU(hatch.getOwnerUuid(), BigInteger.ZERO);
        check(!controller.onRunningTick(null) && state.isRunning(), "unaffordable tick discarded recipe");
        WirelessNetworkManager.setUserEU(hatch.getOwnerUuid(), before);
        for (int tick = 0; tick < controller.mMaxProgresstime; tick++)
            check(controller.onRunningTick(null), "wireless debit failed");
        check(
            hatch.getAvailableEUBig()
                .equals(before.subtract(state.getTotalEUBig())),
            "energy debited incorrectly");
        controller.complete();
        check(
            output.getProvider()
                .getCachedAmountBig()
                .equals(HUGE.multiply(BigInteger.valueOf(fluidMode ? 3 : 5))),
            "guaranteed item outputs lost quantity");
        check(
            output.getFluidProvider()
                .getCachedAmountBig()
                .equals(HUGE.multiply(BigInteger.valueOf(fluidMode ? 293 : 5))),
            "guaranteed fluid mode lost quantity");
        controller.complete();
        check(
            output.getProvider()
                .getCachedAmountBig()
                .equals(HUGE.multiply(BigInteger.valueOf(fluidMode ? 3 : 5))),
            "completion duplicated output");
    }

    private static void verifyProgrammedInput(Source source) {
        ItemStack encoded = AEApi.instance()
            .definitions()
            .items()
            .encodedPattern()
            .maybeStack(1)
            .get();
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList in = new NBTTagList();
        in.appendTag(new ItemStack(Items.diamond, 2).writeToNBT(new NBTTagCompound()));
        NBTTagList out = new NBTTagList();
        out.appendTag(new ItemStack(Items.emerald, 3).writeToNBT(new NBTTagCompound()));
        tag.setTag("in", in);
        tag.setTag("out", out);
        tag.setBoolean("crafting", false);
        encoded.setTagCompound(tag);
        Source programmed = new Source();
        programmed.getPatterns()
            .setInventorySlotContents(0, encoded);
        ArrayList<IAEStack<?>> input = inputs(BigInteger.ONE);
        input.add(AEItemStack.create(reobf.proghatches.item.ItemProgrammingCircuit.wrap(catalyst(), 1)));
        check(
            programmed.pushPatternBig(programmed.getPatternDetails(0), input, HUGE, false),
            "programmed catalyst push failed");
        source.getBuffers()
            .get(0)
            .readNBT(
                programmed.getBuffers()
                    .get(0)
                    .writeNBT());
        check(
            source.getBuffers()
                .get(0)
                .getSelectorCount() == 1,
            "programming wrapper was not decoded into selector");
        check(
            source.getBuffers()
                .get(0)
                .getItemAmountBig()
                .equals(HUGE.multiply(BigInteger.valueOf(2))),
            "programming wrapper became consumable");
    }

    private static void verifyIsolationAndCapacity() throws ReflectiveOperationException {
        Fixture controller = new Fixture(recipe());
        MTEInfiniteEnergyHatch hatch = attach(controller, true);
        output(controller);
        Source source = source(controller, GregtechItemList.PlasticPolymerCatalyst.get(1), HUGE);
        BigPatternBuffer second = source.getBuffers()
            .get(1);
        second.assign(1, null, Collections.singletonList(catalyst()));
        second.add(Collections.singletonList(AEItemStack.create(new ItemStack(Items.diamond))), BigInteger.ONE);
        BigInteger before = hatch.getAvailableEUBig();
        check(
            !((MultiBlockProcessingAccessor) (Object) controller).apeiron$checkRecipe(),
            "isolated recipes borrowed another buffer's catalyst or materials");
        check(
            source.getBuffers()
                .get(0)
                .getItemAmountBig()
                .equals(HUGE.multiply(BigInteger.valueOf(2))),
            "failed selection consumed inputs");
        check(
            hatch.getAvailableEUBig()
                .equals(before),
            "failed catalyst selection consumed EU");
        Fixture full = new Fixture(recipe());
        MTEInfiniteEnergyHatch fullHatch = attach(full, true);
        Source blocked = source(full, catalyst(), HUGE);
        ((BigWirelessController) (Object) full).getWirelessRecipeState()
            .setParallelSettingBig(HUGE);
        check(
            !((MultiBlockProcessingAccessor) (Object) full).apeiron$checkRecipe(),
            "missing output capacity accepted a batch");
        check(
            blocked.getBuffers()
                .get(0)
                .getItemAmountBig()
                .equals(HUGE.multiply(BigInteger.valueOf(2))),
            "output preflight consumed inputs");
        check(
            fullHatch.getAvailableEUBig()
                .equals(BigInteger.TEN.pow(100)),
            "output preflight consumed energy");
        Fixture voltage = new Fixture(recipe());
        attach(voltage, false);
        output(voltage);
        Source overclocked = source(voltage, catalyst(), BigInteger.ONE);
        ((BigWirelessController) (Object) voltage).getWirelessRecipeState()
            .setVoltageSetting(128);
        check(
            ((MultiBlockProcessingAccessor) (Object) voltage).apeiron$checkRecipe(),
            "configured operating voltage did not start recipe");
        check(
            voltage.mMaxProgresstime < 20 && overclocked.getBuffers()
                .get(0)
                .isEmpty(),
            "operating voltage did not apply the native overclock");
        check(
            ((BigWirelessController) (Object) voltage).getWirelessRecipeState()
                .getTotalEUBig()
                .compareTo(BigInteger.valueOf(160)) > 0,
            "native lossy overclock rules were ignored");
    }

    private static void verifyDrop() {
        ApeironMachineTile module = tile(ApeironMachines.QUANTUM_ENHANCEMENT_OFFSET);
        MTEQuantumEnhancementModule machine = (MTEQuantumEnhancementModule) module.getMetaTileEntity();
        check(!machine.isValidItem(catalyst()) && machine.getItemCapacity() == 0, "module accepts physical catalysts");
        ItemStack drop = module.getDrops()
            .get(0);
        check(!drop.hasTagCompound(), "stateless module drop retained NBT");
    }

    private static ApeironMachineTile tile(int offset) {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(offset));
        return tile;
    }

    private static CheckRecipeResult validate(ProcessingLogic logic, GTRecipe recipe)
        throws ReflectiveOperationException {
        Method method = logic.getClass()
            .getDeclaredMethod("validateRecipe", GTRecipe.class);
        method.setAccessible(true);
        return (CheckRecipeResult) method.invoke(logic, recipe);
    }

    private static Field findField(Object instance, String name) throws NoSuchFieldException {
        for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    private static Object field(Object instance, String name) throws ReflectiveOperationException {
        return findField(instance, name).get(instance);
    }

    private static void set(Object instance, String name, Object value) throws ReflectiveOperationException {
        findField(instance, name).set(instance, value);
    }

    private static void check(boolean success, String message) {
        if (!success) throw new IllegalStateException("Quantum enhancement verification: " + message);
    }
}
