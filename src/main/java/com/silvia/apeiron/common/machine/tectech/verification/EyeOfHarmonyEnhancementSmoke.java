package com.silvia.apeiron.common.machine.tectech.verification;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureWalker;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.me.stocking.MTEInfiniteStorageInputAssembly;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.common.machine.tectech.MTEEyeOfHarmonyEnhancementModule;
import com.silvia.apeiron.config.ApeironConfig;

import gregtech.api.enums.Materials;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.structure.error.StructureError;
import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;
import gtneioreplugin.plugin.block.BlockDimensionDisplay;
import tectech.recipe.EyeOfHarmonyRecipe;
import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;

/** Executes the transformed recipe and input checks with detached inventories and wireless accounts. */
public final class EyeOfHarmonyEnhancementSmoke {

    private EyeOfHarmonyEnhancementSmoke() {}

    public static void verify() {
        HashMap<UUID, BigInteger> energy = GlobalVariableStorage.GlobalEnergy;
        Map<UUID, UUID> teams = SpaceProjectManager.spaceTeams;
        GlobalEnergyWorldSavedData save = GlobalEnergyWorldSavedData.INSTANCE;
        try {
            GlobalVariableStorage.GlobalEnergy = new HashMap<>();
            SpaceProjectManager.spaceTeams = new HashMap<>();
            GlobalEnergyWorldSavedData.INSTANCE = new GlobalEnergyWorldSavedData();
            verifyRecipes();
            verifySingleParallelChance();
            EyeOfHarmonyCycleSmoke.verify();
            verifyStructureChecks();
            com.silvia.apeiron.common.machine.me.stocking.verification.StockingInputsSmoke.verifyEyeDrain();
            verifySettings();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Eye of Harmony enhancement verification failed", error);
        } finally {
            GlobalVariableStorage.GlobalEnergy = energy;
            SpaceProjectManager.spaceTeams = teams;
            GlobalEnergyWorldSavedData.INSTANCE = save;
        }
        Apeiron.LOG.info(
            "Eye enhancement verification passed: native chance/time preserved, long parallels, fractional single success, continuous tick cycles, structure errors and settings");
    }

    private static void verifyRecipes() throws ReflectiveOperationException {
        Fixture eye = new Fixture();
        MTEHatchInputBus bus = new MTEHatchInputBus("apeiron.verify.eye_bus", 1, new String[0], null);
        bus.setBaseMetaTileEntity(new BaseMetaTileEntity());
        eye.mInputBusses.add(bus);
        EyeOfHarmonyRecipe recipe = new EyeOfHarmonyRecipe(
            new ArrayList<>(),
            new BlockDimensionDisplay("Ow"),
            1.5D,
            100,
            100,
            20,
            0,
            0.8D);
        prepare(eye, recipe);
        set(eye, "successChance", 0.1D);
        check(
            eye.processRecipe(recipe)
                .wasSuccessful(),
            "native recipe failed");
        check(
            eye.mMaxProgresstime == (Integer) invoke(
                eye,
                "recipeProcessTimeCalculator",
                new Class<?>[] { long.class, long.class },
                recipe.getRecipeTimeInTicks(),
                recipe.getSpacetimeCasingTierRequired()),
            "native time discounts changed without a module");
        check(
            ((Double) get(eye, "successChance")).doubleValue()
                == ((Double) invoke(eye, "recipeChanceCalculator", new Class<?>[0])).doubleValue(),
            "native success calculation changed without a module");

        MTEEyeOfHarmonyEnhancementModule module = module();
        module.setDuration(3);
        module.setSuccessChance(0.5D);
        eye.mInputHatches.add(module);
        prepare(eye, recipe);
        set(eye, "astralArrayAmount", 100_000_000L);
        UUID owner = (UUID) get(eye, "userUUID");
        BigInteger before = WirelessNetworkManager.getUserEU(owner);
        CheckRecipeResult result = eye.processRecipe(recipe);
        check(result.wasSuccessful(), "enhanced recipe failed: " + result.getID());
        long parallels = (Long) get(eye, "parallelAmount");
        check(parallels > Integer.MAX_VALUE, "astral limit was retained");
        check(
            (Long) get(eye, "successfulParallelAmount") == BigDecimal.valueOf(parallels)
                .multiply(BigDecimal.valueOf(0.5D))
                .toBigInteger()
                .longValueExact(),
            "success parallels were clipped to int");
        check(eye.mMaxProgresstime == 3, "configured time was ignored");
        check(
            WirelessNetworkManager.getUserEU(owner)
                .equals(before.add((BigInteger) get(eye, "usedEU"))),
            "native EU settlement changed");
    }

    @SuppressWarnings("unchecked")
    private static void prepare(Fixture eye, EyeOfHarmonyRecipe recipe) throws ReflectiveOperationException {
        UUID owner = UUID.randomUUID();
        WirelessNetworkManager.setUserEU(owner, BigInteger.TEN.pow(60));
        set(eye, "userUUID", owner);
        set(eye, "currentRecipe", recipe);
        set(eye, "animationsEnabled", false);
        set(eye, "spacetimeCompressionFieldMetadata", 0);
        set(eye, "timeAccelerationFieldMetadata", 2);
        set(eye, "stabilisationFieldMetadata", 0);
        Map<Fluid, Long> fluids = (Map<Fluid, Long>) get(eye, "validFluidMap");
        fluids.put(Materials.Hydrogen.mGas, 100L);
        fluids.put(Materials.Helium.mGas, 100L);
        fluids.put(Materials.RawStarMatter.mFluid, Long.MAX_VALUE / 1000);
    }

    private static void verifyStructureChecks() {
        Fixture eye = new Fixture();
        MTEEyeOfHarmonyEnhancementModule enhancement = module();
        MTEInfiniteStorageInputAssembly input = (MTEInfiniteStorageInputAssembly) tile(
            ApeironMachines.STORAGE_INPUT_ASSEMBLY_OFFSET).getMetaTileEntity();
        eye.structureScan = () -> {
            if (!eye.mInputHatches.contains(enhancement)) eye.mInputHatches.add(enhancement);
            if (!eye.mDualInputHatches.contains(input)) eye.mDualInputHatches.add(input);
        };
        eye.mOutputBusses
            .add((gregtech.api.metatileentity.implementations.MTEHatchOutputBus) tile(0).getMetaTileEntity());
        List<StructureError> errors = new ArrayList<>();
        eye.checkMachine(eye.getBaseMetaTileEntity(), null, errors);
        check(!errors.isEmpty(), "missing output hatch was hidden");
        eye.mOutputHatches
            .add((gregtech.api.metatileentity.implementations.MTEHatchOutput) tile(1).getMetaTileEntity());
        errors.clear();
        eye.checkMachine(eye.getBaseMetaTileEntity(), null, errors);
        check(
            errors.isEmpty(),
            "mixed stocking input rejected: " + errors.stream()
                .map(error -> error.getId() + ":" + error.getDisplayString())
                .collect(java.util.stream.Collectors.joining(" | ")));
        check(eye.mInputBusses.size() == 1, "input bus registered more than once");
        check(
            gregtech.api.enums.HatchElement.valueOf("InputHatch")
                .count(eye) == 2,
            "input structure count was not normalized: hatches=" + eye.mInputHatches.size());
    }

    private static void verifySingleParallelChance() throws ReflectiveOperationException {
        Fixture eye = new Fixture();
        MTEHatchInputBus bus = new MTEHatchInputBus("apeiron.verify.eye_chance_bus", 1, new String[0], null);
        bus.setBaseMetaTileEntity(new BaseMetaTileEntity());
        eye.mInputBusses.add(bus);
        MTEEyeOfHarmonyEnhancementModule enhancement = module();
        eye.mInputHatches.add(enhancement);
        EyeOfHarmonyRecipe recipe = new EyeOfHarmonyRecipe(
            new ArrayList<>(),
            new BlockDimensionDisplay("Ow"),
            1.5D,
            100,
            100,
            20,
            0,
            0.8D);
        int successes = 0;
        int failures = 0;
        for (int trial = 0; trial < 128; trial++) {
            prepare(eye, recipe);
            enhancement.setSuccessChance(0.5D);
            check(
                eye.processRecipe(recipe)
                    .wasSuccessful(),
                "fractional single recipe did not start");
            long successful = (Long) get(eye, "successfulParallelAmount");
            check(successful == 0L || successful == 1L, "single recipe produced extra parallels");
            if (successful == 1L) {
                successes++;
                check(
                    ((com.silvia.apeiron.api.machine.tectech.BigEyeOfHarmonyOutput) (Object) eye)
                        .getRecipeFluidOutputBig(0)
                        .signum() > 0,
                    "successful single recipe lost its products");
            } else failures++;
            check((Double) get(eye, "successChance") == 0.5D, "displayed chance disagrees with module");
        }
        check(successes > 0 && failures > 0, "fractional single chance always fails or always succeeds");
        for (double chance : new double[] { 0.0D, 1.0D }) {
            prepare(eye, recipe);
            enhancement.setSuccessChance(chance);
            check(
                eye.processRecipe(recipe)
                    .wasSuccessful(),
                "endpoint chance recipe did not start");
            check((Long) get(eye, "successfulParallelAmount") == (long) chance, "chance endpoint lost certainty");
        }
    }

    private static void verifySettings() {
        MTEEyeOfHarmonyEnhancementModule module = module();
        check(module.getDuration() == 128 && module.getSuccessChance() == 0.5D, "new module defaults changed");
        module.loadNBTData(new NBTTagCompound());
        check(module.getDuration() == 128 && module.getSuccessChance() == 0.5D, "untagged placement defaults changed");
        NBTTagCompound defaults = new NBTTagCompound();
        module.setItemNBT(defaults);
        check(!defaults.hasKey(MTEEyeOfHarmonyEnhancementModule.ROOT_TAG), "default module drop retained settings");
        module.setDuration(0);
        module.setSuccessChance(-1);
        check(module.getDuration() == 1 && module.getSuccessChance() == 0, "settings bounds ignored");
        module.setDuration(47);
        module.setSuccessChance(0.123D);
        NBTTagCompound saved = new NBTTagCompound();
        module.setItemNBT(saved);
        MTEEyeOfHarmonyEnhancementModule restored = module();
        restored.loadNBTData(saved);
        check(restored.getDuration() == 47 && restored.getSuccessChance() == 0.123D, "module settings lost on drop");
    }

    private static MTEEyeOfHarmonyEnhancementModule module() {
        return (MTEEyeOfHarmonyEnhancementModule) tile(ApeironMachines.EYE_OF_HARMONY_ENHANCEMENT_OFFSET)
            .getMetaTileEntity();
    }

    private static ApeironMachineTile tile(int offset) {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(offset));
        return tile;
    }

    private static Object get(Fixture eye, String name) throws ReflectiveOperationException {
        Field field = MTEEyeOfHarmony.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(eye);
    }

    private static void set(Fixture eye, String name, Object value) throws ReflectiveOperationException {
        Field field = MTEEyeOfHarmony.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(eye, value);
    }

    private static Object invoke(Fixture eye, String name, Class<?>[] signature, Object... args)
        throws ReflectiveOperationException {
        Method method = MTEEyeOfHarmony.class.getDeclaredMethod(name, signature);
        method.setAccessible(true);
        return method.invoke(eye, args);
    }

    private static final class Fixture extends MTEEyeOfHarmony {

        private Runnable structureScan = () -> {};

        Fixture() {
            super("apeiron.verify.eye_enhancement");
            setBaseMetaTileEntity(new BaseMetaTileEntity());
        }

        @Override
        public IStructureDefinition<MTEEyeOfHarmony> getStructure_EM() {
            final IStructureDefinition<MTEEyeOfHarmony> empty = StructureDefinition.<MTEEyeOfHarmony>builder()
                .addShape("main", new String[][] { { " " } })
                .build();
            return new IStructureDefinition<MTEEyeOfHarmony>() {

                @Override
                public IStructureElement<MTEEyeOfHarmony>[] getStructureFor(String piece) {
                    return empty.getStructureFor(piece);
                }

                @Override
                public boolean isContainedInStructure(String piece, int a, int b, int c) {
                    return empty.isContainedInStructure(piece, a, b, c);
                }

                @Override
                public void iterate(String piece, World world, ExtendedFacing facing, int x, int y, int z, int a, int b,
                    int c, IStructureWalker<MTEEyeOfHarmony> walker) {
                    // Hatch discovery happens here in a placed machine, after checkMachine has begun.
                    structureScan.run();
                }
            };
        }
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new IllegalStateException("Eye enhancement verification: " + message);
    }
}
