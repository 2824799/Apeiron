package com.silvia.apeiron.common.integration.lanthanides;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.lanthanides.MTEAutoLaserBeamlineInput;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.input.storage.BigPatternBuffer;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.me.output.verification.InfiniteMEOutputAssemblySmoke;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;
import com.silvia.apeiron.mixin.gregtech.output.MultiBlockProcessingAccessor;

import appeng.util.item.AEItemStack;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.multi.compressor.MTEBlackHoleCompressor;
import gtnhlanth.api.recipe.LanthanidesRecipeMaps;
import gtnhlanth.common.beamline.BeamInformation;
import gtnhlanth.common.beamline.BeamLinePacket;
import gtnhlanth.common.hatch.MTEHatchInputBeamline;
import gtnhlanth.common.item.MaskList;
import gtnhlanth.common.register.LanthItemList;
import gtnhlanth.common.tileentity.MTETargetChamber;
import gtnhlanth.common.tileentity.recipe.beamline.TargetChamberMetadata;

/** Detached real controllers: input isolation, native beam policy, optional enhancement and exact output. */
public final class TargetChamberInputSmoke {

    public static boolean enhanced;

    private TargetChamberInputSmoke() {}

    public static class Chamber extends MTETargetChamber {

        private int voltageTier = 4;

        public Chamber() {
            super("apeiron.verify.target_chamber");
            setBaseMetaTileEntity(new BaseMetaTileEntity());
            mWrench = mScrewdriver = mSoftMallet = mHardHammer = mSolderingTool = mCrowbar = true;
        }

        @Override
        public long getInputVoltageTier() {
            return voltageTier;
        }

        @Override
        protected void sendStartMultiBlockSoundLoop() {}

        public void advance() {
            incrementProgressTime();
        }

        public void complete() {
            outputAfterRecipe();
        }

        @SuppressWarnings("unchecked")
        public void mask(MTEHatchInputBus bus) throws ReflectiveOperationException {
            ((ArrayList<MTEHatchInputBus>) field(MTETargetChamber.class, "mMaskInputBusses").get(this)).add(bus);
        }

        @SuppressWarnings("unchecked")
        public void beam() throws ReflectiveOperationException {
            MTEHatchInputBeamline beam = new MTEHatchInputBeamline("apeiron.verify.beam", 4, new String[0], null);
            beam.setBaseMetaTileEntity(new BaseMetaTileEntity());
            beam.dataPacket = new BeamLinePacket(new BeamInformation(150, 1, 0, 100));
            ((ArrayList<MTEHatchInputBeamline>) field(getClass(), "mInputBeamline").get(this)).add(beam);
        }
    }

    private static final class Source extends MTEInfinitePatternInputAssembly {

        Source() {
            super("apeiron.verify.target_source", 10, new String[0], null);
            setBaseMetaTileEntity(new BaseMetaTileEntity());
        }

        @Override
        public boolean isActive() {
            return true;
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    private static MTEHatchInputBus bus(ItemStack... items) {
        MTEHatchInputBus bus = new MTEHatchInputBus("apeiron.verify.target_bus", 4, new String[0], null);
        bus.setBaseMetaTileEntity(new BaseMetaTileEntity());
        for (int i = 0; i < items.length; i++) bus.setInventorySlotContents(i, items[i]);
        return bus;
    }

    private static Source source(ItemStack... items) {
        Source source = new Source();
        add(source, 0, items);
        return source;
    }

    private static void add(Source source, int slot, ItemStack... items) {
        BigPatternBuffer buffer = source.getBuffers()
            .get(slot);
        buffer.assign(slot, null, Collections.emptyList());
        buffer.add(
            Arrays.stream(items)
                .map(AEItemStack::create)
                .collect(java.util.stream.Collectors.toList()),
            BigInteger.ONE);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException("Custom input verification: " + message);
    }

    public static void verify() {
        Field setting = null, resolved = null;
        Object original = null;
        boolean originalResolved = false;
        GTRecipe recipe = GTRecipeBuilder.builder()
            .itemInputs(new ItemStack(Items.diamond, 2), new ItemStack(Items.emerald))
            .itemOutputs(new ItemStack(Items.apple, Integer.MAX_VALUE))
            .metadata(
                LanthanidesRecipeMaps.TARGET_CHAMBER_METADATA,
                TargetChamberMetadata.builder(null)
                    .particleID(0)
                    .amount(1)
                    .energy(100, 200, 1)
                    .minFocus(50)
                    .build())
            .eut(1920)
            .duration(1)
            .build()
            .get();
        LanthanidesRecipeMaps.targetChamberRecipes.addRecipe(recipe);
        try {
            setting = field(TargetChamberEnhancement.class, "enabledField");
            resolved = field(TargetChamberEnhancement.class, "resolved");
            original = setting.get(null);
            originalResolved = resolved.getBoolean(null);
            setting.set(null, TargetChamberInputSmoke.class.getField("enhanced"));
            resolved.setBoolean(null, true);
            enhanced = false;
            Chamber disabled = new Chamber();
            disabled.mInputBusses.add(bus(new ItemStack(Items.diamond, 4), new ItemStack(Items.emerald, 2)));
            disabled.mOutputBusses.add(InfiniteMEOutputAssemblySmoke.assembly());
            check(
                !((MultiBlockProcessingAccessor) disabled).apeiron$checkRecipe(),
                "disabled enhancement bypassed particle checks");
            verifyAutomaticLaser();
            enhanced = true;
            verifyInputs();
            verifyLimits();
            verifyAliasedInputs();
            verifyStocking();
            verifyIsolation();
            verifyNativeMask();
            verifyBlackHoleMode();
            verifySourceChamber();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Custom input fixture failed", error);
        } finally {
            LanthanidesRecipeMaps.targetChamberRecipes.getBackend()
                .removeRecipe(recipe);
            try {
                if (setting != null) setting.set(null, original);
                if (resolved != null) resolved.setBoolean(null, originalResolved);
            } catch (IllegalAccessException error) {
                throw new IllegalStateException(error);
            }
        }
        Apeiron.LOG.info(
            "Custom input verification passed: target chamber native/enhanced, separate mask bus, isolated buffers, exact outputs, beam checks, black hole mode selectors");
    }

    private static ApeironMachineTile laserTile() {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(
            null,
            (short) ApeironConfig.getMachineId(ApeironMachines.AUTO_LASER_BEAMLINE_INPUT_OFFSET));
        check(tile.getMetaTileEntity() instanceof MTEAutoLaserBeamlineInput, "automatic laser placement type");
        check(
            tile.getMetaTileEntity()
                .getStackForm(1)
                .getItem() == net.minecraft.item.Item.getItemFromBlock(ApeironMachines.block),
            "automatic laser item registry");
        return tile;
    }

    private static void verifyAutomaticLaser() throws ReflectiveOperationException {
        for (int mode = 0; mode < 3; mode++) {
            Chamber machine = new Chamber();
            ApeironMachineTile laser = laserTile();
            check(machine.addBeamLineInputHatch(laser, 0), "automatic laser structure registration");
            check(machine.addBeamLineInputHatch(laser, 0), "automatic laser repeated registration");
            check(machine.mInputBeamline.size() == 1, "automatic laser registered twice");
            Source source = null;
            if (mode == 0)
                machine.mInputBusses.add(bus(new ItemStack(Items.diamond, 4), new ItemStack(Items.emerald, 2)));
            else {
                source = source(new ItemStack(Items.diamond, 4), new ItemStack(Items.emerald, 2));
                if (mode == 2) {
                    source = source(new ItemStack(Items.diamond, 2));
                    add(source, 1, new ItemStack(Items.emerald));
                }
                machine.addInputBusToMachineList(source.getBaseMetaTileEntity(), 0);
            }
            MTEInfiniteMEOutputAssembly output = InfiniteMEOutputAssemblySmoke.assembly();
            machine.mOutputBusses.add(output);
            boolean started = ((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe();
            check(started == (mode != 2), "automatic laser mixed isolated patterns");
            if (mode == 2) continue;
            check(machine.mMaxProgresstime == 1 && machine.lEUt == -1920, "automatic laser duration or native energy");
            WirelessRecipeState state = ((BigWirelessController) machine).getWirelessRecipeState();
            check(
                state.usesNativeEnergy() && state.getParallelsBig()
                    .equals(BigInteger.valueOf(2)),
                "automatic laser batch");
            net.minecraft.nbt.NBTTagCompound saved = new net.minecraft.nbt.NBTTagCompound();
            machine.saveNBTData(saved);
            Chamber restored = new Chamber();
            restored.loadNBTData(saved);
            restored.mOutputBusses.add(output);
            restored.advance();
            check(restored.mProgresstime == 1, "automatic laser saved recipe waited for a packet");
            restored.complete();
            BigInteger expected = BigInteger.valueOf(Integer.MAX_VALUE)
                .multiply(BigInteger.valueOf(2));
            check(
                output.getProvider()
                    .getCachedAmountBig()
                    .equals(expected),
                "automatic laser output truncated");
            restored.complete();
            check(
                output.getProvider()
                    .getCachedAmountBig()
                    .equals(expected),
                "automatic laser duplicate completion");
            if (source != null) check(
                source.getBuffers()
                    .get(0)
                    .getItemAmountBig()
                    .signum() == 0,
                "automatic laser debit");
            machine.clearHatches();
            check(machine.mInputBeamline.isEmpty(), "automatic laser scan cleanup");
        }
        Chamber full = new Chamber();
        full.addBeamLineInputHatch(laserTile(), 0);
        Source ingredients = source(new ItemStack(Items.diamond, 4), new ItemStack(Items.emerald, 2));
        full.addInputBusToMachineList(ingredients.getBaseMetaTileEntity(), 0);
        check(!((MultiBlockProcessingAccessor) full).apeiron$checkRecipe(), "automatic laser accepted missing output");
        check(
            ingredients.getBuffers()
                .get(0)
                .getItemAmountBig()
                .equals(BigInteger.valueOf(6)),
            "automatic laser lost inputs");

        gregtech.common.tileentities.machines.multi.beamcrafting.MTEBeamCrafter crafter = new gregtech.common.tileentities.machines.multi.beamcrafting.MTEBeamCrafter(
            "apeiron.verify.laser_wrong_machine");
        check(!crafter.addBeamLineInputHatch(laserTile(), 0), "automatic laser accepted by beam crafter");
        check(crafter.mInputBeamline.isEmpty(), "automatic laser leaked into another machine");
        verifyAutomaticLaserMask();
        verifyAutomaticLaserCapacity();
        Apeiron.LOG.info(
            "Automatic laser verification passed: placement, target-only registration, 1 tick, native EU, isolated inputs, mask roles, photon-only recipes, output protection, reload and exact outputs");
    }

    private static void verifyAutomaticLaserMask() throws ReflectiveOperationException {
        ItemStack mask = new ItemStack(LanthItemList.maskMap.get(MaskList.ASOC));
        GTRecipe recipe = GTRecipeBuilder.builder()
            .itemInputs(mask, new ItemStack(Items.ender_pearl))
            .itemOutputs(new ItemStack(Items.apple))
            .metadata(
                LanthanidesRecipeMaps.TARGET_CHAMBER_METADATA,
                TargetChamberMetadata.builder(mask)
                    .particleID(0)
                    .amount(Integer.MAX_VALUE)
                    .energy(5, 6, 1)
                    .minFocus(99)
                    .build())
            .eut(1920)
            .duration(1)
            .build()
            .get();
        LanthanidesRecipeMaps.targetChamberRecipes.addRecipe(recipe);
        try {
            for (int mode = 0; mode < 4; mode++) {
                Chamber machine = new Chamber();
                machine.addBeamLineInputHatch(laserTile(), 0);
                machine.mOutputBusses.add(InfiniteMEOutputAssemblySmoke.assembly());
                if (mode == 0) {
                    machine.mask(bus(mask.copy()));
                    machine.mInputBusses.add(bus(new ItemStack(Items.ender_pearl)));
                } else if (mode == 1) {
                    machine.mask(source(mask.copy()));
                    machine
                        .addInputBusToMachineList(source(new ItemStack(Items.ender_pearl)).getBaseMetaTileEntity(), 0);
                } else if (mode == 2) {
                    machine.mask(bus(new ItemStack(Items.ender_pearl)));
                    machine.mInputBusses.add(bus(mask.copy()));
                } else machine.mInputBusses.add(bus(new ItemStack(Items.ender_pearl)));
                check(
                    ((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe() == (mode < 2),
                    "automatic laser mask role " + mode);
                if (mode < 2) check(machine.mMaxProgresstime == 1, "automatic laser large photon count was not 1 tick");
            }
        } finally {
            LanthanidesRecipeMaps.targetChamberRecipes.getBackend()
                .removeRecipe(recipe);
        }
        GTRecipe nonPhoton = GTRecipeBuilder.builder()
            .itemInputs(new ItemStack(Items.blaze_rod))
            .itemOutputs(new ItemStack(Items.apple))
            .metadata(
                LanthanidesRecipeMaps.TARGET_CHAMBER_METADATA,
                TargetChamberMetadata.builder(null)
                    .particleID(gtnhlanth.common.beamline.Particle.ELECTRON.getId())
                    .amount(1)
                    .energy(100, 200, 1)
                    .minFocus(50)
                    .build())
            .eut(1920)
            .duration(1)
            .build()
            .get();
        LanthanidesRecipeMaps.targetChamberRecipes.addRecipe(nonPhoton);
        try {
            Chamber machine = new Chamber();
            machine.addBeamLineInputHatch(laserTile(), 0);
            machine.mInputBusses.add(bus(new ItemStack(Items.blaze_rod)));
            machine.mOutputBusses.add(InfiniteMEOutputAssemblySmoke.assembly());
            check(
                !((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe(),
                "automatic laser provided non-photons");
        } finally {
            LanthanidesRecipeMaps.targetChamberRecipes.getBackend()
                .removeRecipe(nonPhoton);
        }
    }

    private static void verifyAutomaticLaserCapacity() {
        GTRecipe recipe = GTRecipeBuilder.builder()
            .itemInputs(new ItemStack(Items.stick))
            .itemOutputs(new ItemStack(Items.apple))
            .metadata(
                LanthanidesRecipeMaps.TARGET_CHAMBER_METADATA,
                TargetChamberMetadata.builder(null)
                    .particleID(0)
                    .amount(1)
                    .energy(100, 200, 1)
                    .minFocus(50)
                    .build())
            .eut(1920)
            .duration(1)
            .build()
            .get();
        LanthanidesRecipeMaps.targetChamberRecipes.addRecipe(recipe);
        try {
            Chamber machine = new Chamber();
            machine.addBeamLineInputHatch(laserTile(), 0);
            ItemStack sticks = new ItemStack(Items.stick, Integer.MAX_VALUE);
            machine.mInputBusses.add(bus(sticks));
            gregtech.api.metatileentity.implementations.MTEHatchOutputBus output = new gregtech.api.metatileentity.implementations.MTEHatchOutputBus(
                "apeiron.verify.laser_capacity",
                4,
                new String[0],
                null);
            output.setBaseMetaTileEntity(new BaseMetaTileEntity());
            machine.mOutputBusses.add(output);
            check(
                ((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe(),
                "automatic laser capacity search failed");
            BigInteger parallels = ((BigWirelessController) machine).getWirelessRecipeState()
                .getParallelsBig();
            check(
                parallels.signum() > 0 && parallels.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) < 0,
                "automatic laser did not limit batch to finite output");
            check(sticks.stackSize == Integer.MAX_VALUE - parallels.intValueExact(), "automatic laser capacity debit");
            machine.advance();
            machine.complete();
            long total = 0;
            for (int slot = 0; slot < output.getSizeInventory(); slot++) {
                ItemStack stack = output.getStackInSlot(slot);
                if (stack != null) total += stack.stackSize;
            }
            check(
                BigInteger.valueOf(total)
                    .equals(parallels),
                "automatic laser finite output lost items");
        } finally {
            LanthanidesRecipeMaps.targetChamberRecipes.getBackend()
                .removeRecipe(recipe);
        }
    }

    private static void verifyInputs() throws ReflectiveOperationException {
        for (int mode = 0; mode < 3; mode++) {
            Chamber machine = new Chamber();
            MTEInfiniteMEOutputAssembly output = InfiniteMEOutputAssemblySmoke.assembly();
            machine.mOutputBusses.add(output);
            Source source = null;
            if (mode == 0)
                machine.mInputBusses.add(bus(new ItemStack(Items.diamond, 4), new ItemStack(Items.emerald, 2)));
            else if (mode == 1) {
                source = source(new ItemStack(Items.diamond, 4), new ItemStack(Items.emerald, 2));
                machine.addInputBusToMachineList(source.getBaseMetaTileEntity(), 0);
            } else {
                source = source(new ItemStack(Items.diamond, 4));
                machine.addInputBusToMachineList(source.getBaseMetaTileEntity(), 0);
                machine.mask(bus(new ItemStack(Items.emerald, 2)));
            }
            check(((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe(), "enhanced input mode " + mode);
            WirelessRecipeState state = ((BigWirelessController) machine).getWirelessRecipeState();
            check(
                state.getParallelsBig()
                    .equals(BigInteger.valueOf(2)),
                "parallel input count");
            check(machine.mMaxProgresstime == 20 && machine.lEUt == -1920, "enhancement duration or native EU");
            check(state.usesNativeEnergy(), "target chamber bypassed native energy");
            for (int tick = 0; tick < 20; tick++) machine.advance();
            check(machine.mProgresstime == 20, "enhancement still waits for particles while running");
            if (source != null) check(
                source.getBuffers()
                    .get(0)
                    .getItemAmountBig()
                    .signum() == 0,
                "pattern inputs not consumed");
            machine.complete();
            check(
                output.getProvider()
                    .getCachedAmountBig()
                    .equals(
                        BigInteger.valueOf(Integer.MAX_VALUE)
                            .multiply(BigInteger.valueOf(2))),
                "enhancement output truncated or duplicated");
            machine.complete();
            check(
                output.getProvider()
                    .getCachedAmountBig()
                    .equals(
                        BigInteger.valueOf(Integer.MAX_VALUE)
                            .multiply(BigInteger.valueOf(2))),
                "repeated completion duplicated outputs");
        }
        Chamber full = new Chamber();
        Source source = source(new ItemStack(Items.diamond, 4), new ItemStack(Items.emerald, 2));
        full.addInputBusToMachineList(source.getBaseMetaTileEntity(), 0);
        check(!((MultiBlockProcessingAccessor) full).apeiron$checkRecipe(), "missing output accepted");
        check(
            source.getBuffers()
                .get(0)
                .getItemAmountBig()
                .equals(BigInteger.valueOf(6)),
            "failed capacity consumed input");
    }

    private static void verifyAliasedInputs() {
        Chamber machine = new Chamber();
        machine.mOutputBusses.add(InfiniteMEOutputAssemblySmoke.assembly());
        ItemStack diamond = new ItemStack(Items.diamond, 2);
        ItemStack emerald = new ItemStack(Items.emerald);
        check(
            TargetChamberEnhancement.process(machine, new ItemStack[] { diamond, emerald, diamond, emerald })
                .wasSuccessful(),
            "aliased input rejected");
        check(
            ((BigWirelessController) machine).getWirelessRecipeState()
                .getParallelsBig()
                .equals(BigInteger.ONE),
            "aliased input counted more than once");
        check(diamond.stackSize == 0 && emerald.stackSize == 0, "aliased input debit");
    }

    private static void verifyLimits() {
        Chamber machine = new Chamber();
        machine.voltageTier = 15;
        machine.mOutputBusses.add(InfiniteMEOutputAssemblySmoke.assembly());
        int supplied = TargetChamberEnhancement.MAX_PARALLEL + 1;
        Source input = source(new ItemStack(Items.diamond, supplied * 2), new ItemStack(Items.emerald, supplied));
        machine.addInputBusToMachineList(input.getBaseMetaTileEntity(), 0);
        check(((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe(), "large enhanced batch rejected");
        WirelessRecipeState state = ((BigWirelessController) machine).getWirelessRecipeState();
        check(
            state.getParallelsBig()
                .equals(BigInteger.valueOf(TargetChamberEnhancement.MAX_PARALLEL)),
            "enhancement cap changed");
        check(
            input.getBuffers()
                .get(0)
                .getItemAmountBig()
                .equals(BigInteger.valueOf(3)),
            "batch cap input debit");
        check(
            machine.lEUt == -gregtech.api.enums.GTValues.VP[15] && machine.lEUt < -Integer.MAX_VALUE,
            "high-voltage native energy narrowed to int");
        net.minecraft.nbt.NBTTagCompound tag = state.save();
        WirelessRecipeState restored = new WirelessRecipeState();
        restored.load(tag);
        check(
            restored.usesNativeEnergy() && restored.getParallelsBig()
                .equals(state.getParallelsBig()),
            "saved target batch lost native energy or count");
    }

    private static void verifyStocking() {
        for (boolean fail : new boolean[] { false, true }) {
            Chamber machine = new Chamber();
            machine.mOutputBusses.add(InfiniteMEOutputAssemblySmoke.assembly());
            com.silvia.apeiron.common.machine.me.stocking.verification.StockingInputsSmoke
                .verifySplitItemInputs(machine, (rear, front) -> {
                    machine.addInputBusToMachineList(rear.getBaseMetaTileEntity(), 0);
                    try {
                        machine.mask(front);
                    } catch (ReflectiveOperationException error) {
                        throw new IllegalStateException(error);
                    }
                }, fail);
        }
        for (boolean front : new boolean[] { false, true }) {
            Chamber machine = new Chamber();
            machine.mOutputBusses.add(InfiniteMEOutputAssemblySmoke.assembly());
            if (front) machine.mInputBusses.add(bus(new ItemStack(Items.diamond, 4)));
            com.silvia.apeiron.common.machine.me.stocking.verification.StockingInputsSmoke
                .verifyCustomItemInputs(machine, input -> {
                    if (!front) machine.addInputBusToMachineList(input.getBaseMetaTileEntity(), 0);
                    else try {
                        machine.mask(input);
                    } catch (ReflectiveOperationException failure) {
                        throw new IllegalStateException(failure);
                    }
                },
                    front ? new ItemStack[] { new ItemStack(Items.emerald, 2) }
                        : new ItemStack[] { new ItemStack(Items.diamond, 4), new ItemStack(Items.emerald, 2) });
        }
    }

    private static void verifyIsolation() {
        Chamber machine = new Chamber();
        machine.mOutputBusses.add(InfiniteMEOutputAssemblySmoke.assembly());
        Source source = source(new ItemStack(Items.diamond, 4));
        add(source, 1, new ItemStack(Items.emerald, 2));
        machine.addInputBusToMachineList(source.getBaseMetaTileEntity(), 0);
        check(!((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe(), "unrelated buffers were merged");
        check(
            source.getBuffers()
                .get(0)
                .getItemAmountBig()
                .equals(BigInteger.valueOf(4)),
            "failed isolated recipe consumed input");
        add(source, 2, new ItemStack(Items.diamond, 4), new ItemStack(Items.emerald, 2));
        check(((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe(), "later matching buffer was not tried");
        check(
            source.getBuffers()
                .get(1)
                .getItemAmountBig()
                .equals(BigInteger.valueOf(2)),
            "other buffer consumed");
    }

    private static void verifyNativeMask() throws ReflectiveOperationException {
        enhanced = false;
        ItemStack mask = new ItemStack(LanthItemList.maskMap.get(MaskList.ASOC), 1);
        GTRecipe recipe = GTRecipeBuilder.builder()
            .itemInputs(mask, new ItemStack(Items.ender_pearl))
            .itemOutputs(new ItemStack(Items.apple))
            .metadata(
                LanthanidesRecipeMaps.TARGET_CHAMBER_METADATA,
                TargetChamberMetadata.builder(mask)
                    .particleID(0)
                    .amount(1)
                    .energy(100, 200, 1)
                    .minFocus(50)
                    .build())
            .eut(1920)
            .duration(1)
            .build()
            .get();
        LanthanidesRecipeMaps.targetChamberRecipes.addRecipe(recipe);
        try {
            for (boolean patternMask : new boolean[] { false, true }) {
                Chamber machine = new Chamber();
                Source material = source(new ItemStack(Items.ender_pearl));
                machine.addInputBusToMachineList(material.getBaseMetaTileEntity(), 0);
                MTEHatchInputBus masks = patternMask ? source(mask.copy()) : bus(mask.copy());
                machine.mask(masks);
                machine.mOutputBusses.add(InfiniteMEOutputAssemblySmoke.assembly());
                check(
                    !((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe(),
                    "native patterned recipe ignored beam");
                machine.beam();
                check(
                    ((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe(),
                    "native patterned recipe not found");
                check(
                    material.getBuffers()
                        .get(0)
                        .getItemAmountBig()
                        .signum() == 0,
                    "native patterned material unconsumed");
                if (patternMask) check(
                    ((Source) masks).getBuffers()
                        .get(0)
                        .getItemAmountBig()
                        .signum() == 0,
                    "pattern mask unconsumed");
                else check(
                    masks.getStackInSlot(0) == null || masks.getStackInSlot(0).stackSize == 0,
                    "mask unconsumed");
            }
        } finally {
            LanthanidesRecipeMaps.targetChamberRecipes.getBackend()
                .removeRecipe(recipe);
            enhanced = true;
        }
    }

    private static void verifySourceChamber() {
        gtnhlanth.common.tileentity.MTESourceChamber machine = new gtnhlanth.common.tileentity.MTESourceChamber(
            "apeiron.verify.source_chamber") {

            @Override
            public long getInputVoltageTier() {
                return 4;
            }

            @Override
            public long getMaxInputVoltage() {
                return 8192;
            }

            @Override
            protected void sendStartMultiBlockSoundLoop() {}
        };
        machine.setBaseMetaTileEntity(new BaseMetaTileEntity());
        machine.mWrench = machine.mScrewdriver = machine.mSoftMallet = machine.mHardHammer = machine.mSolderingTool = machine.mCrowbar = true;
        Source source = source(new ItemStack(Items.diamond, 2));
        net.minecraftforge.fluids.FluidStack water = new net.minecraftforge.fluids.FluidStack(
            net.minecraftforge.fluids.FluidRegistry.WATER,
            40);
        source.getBuffers()
            .get(1)
            .assign(1, null, Collections.emptyList());
        source.getBuffers()
            .get(1)
            .add(Collections.singletonList(appeng.util.item.AEFluidStack.create(water)), BigInteger.ONE);
        machine.addInputBusToMachineList(source.getBaseMetaTileEntity(), 0);
        GTRecipe recipe = GTRecipeBuilder.builder()
            .itemInputs(new ItemStack(Items.diamond, 2))
            .fluidInputs(water)
            .itemOutputs(new ItemStack(Items.apple))
            .duration(20)
            .eut(1920)
            .metadata(
                LanthanidesRecipeMaps.SOURCE_CHAMBER_METADATA,
                gtnhlanth.common.tileentity.recipe.beamline.SourceChamberMetadata.builder()
                    .particleID(gtnhlanth.common.beamline.Particle.ELECTRON.getId())
                    .rate(20)
                    .energy(1000, 0.1f)
                    .focus(98)
                    .build())
            .build()
            .get();
        LanthanidesRecipeMaps.sourceChamberRecipes.addRecipe(recipe);
        try {
            check(
                !((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe(),
                "source chamber merged isolated item/fluid buffers");
            source.getBuffers()
                .get(0)
                .add(Collections.singletonList(appeng.util.item.AEFluidStack.create(water)), BigInteger.ONE);
            check(
                ((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe(),
                "source chamber did not read isolated item/fluid inputs");
            check(
                source.getBuffers()
                    .get(0)
                    .getItemAmountBig()
                    .signum() == 0
                    && source.getBuffers()
                        .get(0)
                        .getFluidAmountBig()
                        .signum() == 0,
                "source chamber failed to debit both channels");
            check(
                source.getBuffers()
                    .get(1)
                    .getFluidAmountBig()
                    .equals(BigInteger.valueOf(40)),
                "source chamber debited another pattern");
        } finally {
            LanthanidesRecipeMaps.sourceChamberRecipes.getBackend()
                .removeRecipe(recipe);
        }
    }

    private static void verifyBlackHoleMode() throws ReflectiveOperationException {
        MTEBlackHoleCompressor machine = new MTEBlackHoleCompressor("apeiron.verify.black_hole") {

            @Override
            public long getAverageInputVoltage() {
                return 8192;
            }

            @Override
            public long getMaxInputAmps() {
                return 1;
            }

            @Override
            protected void sendStartMultiBlockSoundLoop() {}
        };
        machine.setBaseMetaTileEntity(new BaseMetaTileEntity());
        field(MTEBlackHoleCompressor.class, "blackHoleStatus").setByte(machine, (byte) 2);
        machine.mWrench = machine.mScrewdriver = machine.mSoftMallet = machine.mHardHammer = machine.mSolderingTool = machine.mCrowbar = true;
        machine.mOutputBusses.add(InfiniteMEOutputAssemblySmoke.assembly());
        Source source = source(new ItemStack(Items.diamond, 18));
        machine.addInputBusToMachineList(source.getBaseMetaTileEntity(), 0);
        GTRecipe recipe = GTRecipeBuilder.builder()
            .itemInputs(new ItemStack(Items.diamond, 9))
            .itemOutputs(new ItemStack(Items.apple))
            .duration(100)
            .eut(8)
            .build()
            .get();
        RecipeMaps.compressorRecipes.addRecipe(recipe);
        try {
            check(
                !((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe(),
                "black hole accepted missing mode circuit");
            source.setInventorySlotContents(
                MTEInfinitePatternInputAssembly.SHARED_CATALYST_START,
                GTUtility.getIntegratedCircuit(20));
            check(
                ((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe(),
                "black hole rejected pattern with circuit 20");
            check(
                source.getBuffers()
                    .get(0)
                    .getItemAmountBig()
                    .compareTo(BigInteger.valueOf(18)) < 0,
                "black hole consumed no patterned items");
        } finally {
            RecipeMaps.compressorRecipes.getBackend()
                .removeRecipe(recipe);
        }
    }
}
