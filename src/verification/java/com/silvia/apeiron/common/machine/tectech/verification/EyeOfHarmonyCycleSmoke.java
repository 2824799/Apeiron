package com.silvia.apeiron.common.machine.tectech.verification;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.api.machine.tectech.BigEyeOfHarmonyOutput;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.common.machine.tectech.MTEEyeOfHarmonyEnhancementModule;
import com.silvia.apeiron.config.ApeironConfig;

import gregtech.api.enums.Materials;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.common.misc.WirelessNetworkManager;
import gtneioreplugin.plugin.block.BlockDimensionDisplay;
import gtneioreplugin.plugin.block.ModBlocks;
import tectech.TecTech;
import tectech.recipe.EyeOfHarmonyRecipe;
import tectech.recipe.EyeOfHarmonyRecipeStorage;
import tectech.thing.block.TileEntityEyeOfHarmony;
import tectech.thing.casing.TTCasingsContainer;
import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;

/** Exercises the real pre/post tick lifecycle while keeping world, recipe lookup and accounts detached. */
public final class EyeOfHarmonyCycleSmoke {

    private EyeOfHarmonyCycleSmoke() {}

    public static void verify() throws ReflectiveOperationException {
        EyeOfHarmonyRecipeStorage original = TecTech.eyeOfHarmonyRecipeStorage;
        try {
            Lookup lookup = allocate(Lookup.class);
            lookup.recipe = new EyeOfHarmonyRecipe(
                new ArrayList<>(java.util.Collections.singletonList(Pair.of(Materials.Iron, 100L))),
                new BlockDimensionDisplay("Ow"),
                1.5D,
                100,
                100,
                20,
                0,
                0.8D);
            TecTech.eyeOfHarmonyRecipeStorage = lookup;
            verifyContinuous(lookup.recipe, 1, 16);
            verifyContinuous(lookup.recipe, 3, 12);
            verifyContinuous(lookup.recipe, 128, 128);
            verifyStarvation(lookup.recipe);
            verifyDisabledAndUnenhanced(lookup.recipe);
            verifyAnimationToggle(lookup.recipe);
        } finally {
            TecTech.eyeOfHarmonyRecipeStorage = original;
        }
        Apeiron.LOG.info(
            "Eye cycle verification passed: same-tick startup, one work tick per callback, continuous render, input/EU conservation and idle cleanup");
    }

    private static void verifyContinuous(EyeOfHarmonyRecipe recipe, int duration, int ticks)
        throws ReflectiveOperationException {
        int completed = ticks / duration;
        Fixture eye = new Fixture(recipe, duration, completed + 1, true);
        BigInteger before = WirelessNetworkManager.getUserEU(eye.owner);
        TileEntity renderer = null;
        for (int tick = 1; tick <= ticks; tick++) {
            eye.tick(tick + 200L);
            check(eye.workTicks == tick, "work advanced twice or missed a callback");
            check(eye.completed == tick / duration, "duration added an input-only tick");
            check(eye.tile.isActive(), "continuous machine went idle between batches");
            if (renderer == null) renderer = eye.world.renderer;
            check(renderer != null && eye.world.renderer == renderer, "renderer replaced between recipes");
        }
        check(eye.started == completed + 1, "native next-batch reservation changed");
        check(eye.world.creations == 1 && eye.world.removals == 1, "continuous animation flickered");
        check(eye.hydrogen.amount == 0 && eye.helium.amount == 0, "input charged twice or not charged");
        check(
            ((BigEyeOfHarmonyOutput) (Object) eye).getPendingItemOutputBig()
                .equals(eye.itemsPerBatch.multiply(BigInteger.valueOf(completed))),
            "completed item products duplicated or lost");
        check(
            ((BigEyeOfHarmonyOutput) (Object) eye).getPendingFluidOutputBig()
                .equals(eye.fluidsPerBatch.multiply(BigInteger.valueOf(completed))),
            "completed fluid products duplicated or lost");
        check(
            WirelessNetworkManager.getUserEU(eye.owner)
                .equals(
                    before.add(eye.inputEU.multiply(BigInteger.valueOf(eye.started)))
                        .add(eye.outputEU.multiply(BigInteger.valueOf(eye.completed)))),
            "native startup/completion EU charged twice");
        eye.tile.allowed = false;
        for (int tick = 1; tick <= duration; tick++) eye.tick(400L + tick);
        check(!eye.tile.isActive() && eye.world.renderer == null, "shutdown kept a stale renderer");
    }

    private static void verifyStarvation(EyeOfHarmonyRecipe recipe) throws ReflectiveOperationException {
        Fixture eye = new Fixture(recipe, 1, 1, true);
        eye.tick(201L);
        check(eye.completed == 1 && eye.started == 1, "one supplied batch did not finish in one tick");
        check(!eye.tile.isActive() && eye.world.renderer == null, "missing inputs kept the animation alive");
        BigInteger energy = WirelessNetworkManager.getUserEU(eye.owner);
        for (int tick = 202; tick < 210; tick++) eye.tick(tick);
        check(
            eye.completed == 1 && WirelessNetworkManager.getUserEU(eye.owner)
                .equals(energy),
            "starved machine repeated output or debit");
        Fixture noPower = new Fixture(recipe, 1, 1, true);
        WirelessNetworkManager.setUserEU(noPower.owner, BigInteger.ZERO);
        noPower.tick(201L);
        check(
            noPower.started == 0 && noPower.completed == 0 && noPower.world.renderer == null,
            "insufficient startup power ran the recipe");
        WirelessNetworkManager.setUserEU(noPower.owner, BigInteger.TEN.pow(60));
        noPower.tick(202L);
        check(noPower.completed == 1, "refunded/reserved input could not resume after power returned");
    }

    private static void verifyDisabledAndUnenhanced(EyeOfHarmonyRecipe recipe) throws ReflectiveOperationException {
        Fixture disabled = new Fixture(recipe, 1, 2, true);
        disabled.tile.allowed = false;
        disabled.tick(201L);
        check(disabled.started == 0 && disabled.completed == 0, "disabled controller started a recipe");
        Fixture nativeEye = new Fixture(recipe, 1, 2, false);
        nativeEye.tick(201L);
        check(nativeEye.completed == 0, "unenhanced native startup was accelerated");
    }

    private static void verifyAnimationToggle(EyeOfHarmonyRecipe recipe) throws ReflectiveOperationException {
        Fixture eye = new Fixture(recipe, 1, 4, true);
        eye.tick(201L);
        check(eye.world.renderer != null, "initial enhanced animation is absent");
        set(eye, "animationsEnabled", false);
        eye.tick(202L);
        check(
            eye.completed == 2 && eye.tile.isActive() && eye.world.renderer == null,
            "disabling animation stopped work or retained the renderer");
        set(eye, "animationsEnabled", true);
        eye.tick(203L);
        check(
            eye.completed == 3 && eye.world.renderer != null && eye.world.creations == 2,
            "re-enabling animation did not restore the native renderer");
    }

    private static final class Fixture extends MTEEyeOfHarmony {

        private final UUID owner = UUID.randomUUID();
        private final RenderWorld world;
        private final FixtureTile tile = new FixtureTile();
        private final FluidStack hydrogen;
        private final FluidStack helium;
        private int workTicks, started, completed;
        private BigInteger inputEU, outputEU, itemsPerBatch, fluidsPerBatch;

        Fixture(EyeOfHarmonyRecipe recipe, int duration, int batches, boolean enhanced)
            throws ReflectiveOperationException {
            super("apeiron.verify.eye_cycle");
            world = allocate(RenderWorld.class);
            tile.setWorldObj(world);
            setBaseMetaTileEntity(tile);
            hydrogen = Materials.Hydrogen.getGas(100 * batches);
            helium = Materials.Helium.getGas(100 * batches);
            MTEHatchInputBus bus = new MTEHatchInputBus("apeiron.verify.eye_cycle_bus", 1, new String[0], null);
            bus.setBaseMetaTileEntity(new BaseMetaTileEntity());
            mInputBusses.add(bus);
            if (enhanced) {
                ApeironMachineTile moduleTile = new ApeironMachineTile();
                moduleTile.setInitialValuesAsNBT(
                    null,
                    (short) ApeironConfig.getMachineId(ApeironMachines.EYE_OF_HARMONY_ENHANCEMENT_OFFSET));
                MTEEyeOfHarmonyEnhancementModule module = (MTEEyeOfHarmonyEnhancementModule) moduleTile
                    .getMetaTileEntity();
                module.setDuration(duration);
                module.setSuccessChance(1.0D);
                mInputHatches.add(module);
            }
            mInventory[getControllerSlotIndex()] = new ItemStack(ModBlocks.getBlock("Ow"));
            mMachine = true;
            mStartUpCheck = -1;
            mUpdate = 0;
            mWrench = mScrewdriver = mSoftMallet = mHardHammer = mSolderingTool = mCrowbar = true;
            set(this, "userUUID", owner);
            set(this, "spacetimeCompressionFieldMetadata", 0);
            set(this, "timeAccelerationFieldMetadata", 2);
            set(this, "stabilisationFieldMetadata", 0);
            WirelessNetworkManager.setUserEU(owner, BigInteger.TEN.pow(60));
        }

        private void tick(long tick) {
            onPreTick(tile, tick);
            onPostTick(tile, tick);
        }

        @Override
        public ArrayList<FluidStack> getStoredFluids() {
            return new ArrayList<>(Arrays.asList(hydrogen, helium));
        }

        @Override
        public CheckRecipeResult processRecipe(EyeOfHarmonyRecipe recipe) {
            CheckRecipeResult result = super.processRecipe(recipe);
            if (result.wasSuccessful()) {
                started++;
                try {
                    inputEU = (BigInteger) get(this, "usedEU");
                    outputEU = (BigInteger) get(this, "outputEU_BigInt");
                    List<?> items = (List<?>) get(this, "outputItems");
                    List<?> fluids = (List<?>) get(this, "outputFluids");
                    itemsPerBatch = BigInteger.ZERO;
                    fluidsPerBatch = BigInteger.ZERO;
                    for (int index = 0; index < items.size(); index++) itemsPerBatch = itemsPerBatch
                        .add(((BigEyeOfHarmonyOutput) (Object) this).getRecipeItemOutputBig(index));
                    for (int index = 0; index < fluids.size(); index++) fluidsPerBatch = fluidsPerBatch
                        .add(((BigEyeOfHarmonyOutput) (Object) this).getRecipeFluidOutputBig(index));
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException(failure);
                }
            }
            return result;
        }

        @Override
        public void outputAfterRecipe_EM() {
            completed++;
            super.outputAfterRecipe_EM();
        }

        @Override
        public boolean onRunningTick(ItemStack stack) {
            workTicks++;
            return super.onRunningTick(stack);
        }

        @Override
        public boolean doRandomMaintenanceDamage() {
            return true;
        }

        @Override
        public boolean polluteEnvironment(int amount) {
            return true;
        }

        @Override
        protected void dischargeController_EM(IGregTechTileEntity ignored) {}

        @Override
        protected void chargeController_EM(IGregTechTileEntity ignored) {}
    }

    private static final class FixtureTile extends BaseMetaTileEntity {

        private boolean allowed = true;

        @Override
        public void markDirty() {}

        @Override
        public boolean isAllowedToWork() {
            return allowed;
        }

        @Override
        public boolean hasWorkJustBeenEnabled() {
            return false;
        }
    }

    private static final class Lookup extends EyeOfHarmonyRecipeStorage {

        private EyeOfHarmonyRecipe recipe;

        @Override
        public EyeOfHarmonyRecipe recipeLookUp(ItemStack planet) {
            return recipe;
        }
    }

    private static final class RenderWorld extends World {

        private Block block;
        private TileEntityEyeOfHarmony renderer;
        private int creations, removals;

        private RenderWorld() {
            super(null, "apeiron.verify.eye_render", (net.minecraft.world.WorldSettings) null, null, null);
            throw new AssertionError("Allocate detached world without its constructor");
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            return block == null ? Blocks.air : block;
        }

        @Override
        public TileEntity getTileEntity(int x, int y, int z) {
            return renderer;
        }

        @Override
        public boolean setBlock(int x, int y, int z, Block newBlock, int meta, int flags) {
            block = newBlock;
            if (block == TTCasingsContainer.eyeOfHarmonyRenderBlock) {
                creations++;
                renderer = new TileEntityEyeOfHarmony();
                renderer.setWorldObj(this);
            } else {
                removals++;
                renderer = null;
            }
            return true;
        }

        @Override
        public void markTileEntityChunkModified(int x, int y, int z, TileEntity tile) {}

        @Override
        public void markBlockForUpdate(int x, int y, int z) {}

        @Override
        protected IChunkProvider createChunkProvider() {
            return null;
        }

        @Override
        protected int func_152379_p() {
            return 0;
        }

        @Override
        public Entity getEntityByID(int id) {
            return null;
        }
    }

    private static <T> T allocate(Class<T> type) throws ReflectiveOperationException {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafe.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        return type.cast(
            unsafe.getMethod("allocateInstance", Class.class)
                .invoke(singleton.get(null), type));
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

    private static void check(boolean valid, String message) {
        if (!valid) throw new IllegalStateException("Eye cycle verification: " + message);
    }
}
