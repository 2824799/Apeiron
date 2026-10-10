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
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputMirror;
import com.silvia.apeiron.common.machine.me.input.storage.BigPatternBuffer;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.me.output.verification.InfiniteMEOutputAssemblySmoke;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
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

/** Detached real controllers: input isolation, native beam policy, paid particles and exact output. */
public final class TargetChamberInputSmoke {

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

    private static final class Mirror extends MTEInfinitePatternInputMirror {

        private final Source source;

        private Mirror(Source source) {
            super("apeiron.verify.target_mirror", 10, new String[0], null);
            this.source = source;
            setBaseMetaTileEntity(new BaseMetaTileEntity());
            if (source != null) setLink(0, 10, 20, 30);
        }

        @Override
        protected MTEInfinitePatternInputAssembly lookupSource() {
            return source;
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

    private static ItemStack particle(int id, int amount) {
        return new ItemStack(LanthItemList.PARTICLE_ITEM, amount, id);
    }

    private static ItemStack photon(int amount) {
        return particle(gtnhlanth.common.beamline.Particle.PHOTON.getId(), amount);
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
            verifyPatternParticles();
            verifyPatternGate();
            verifyRegisteredGTRecipes();
            verifyNativeBeam();
            verifySourceChamber();
            verifyBlackHoleMode();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Custom input fixture failed", error);
        } finally {
            LanthanidesRecipeMaps.targetChamberRecipes.getBackend()
                .removeRecipe(recipe);
        }
        check(
            gregtech.api.GregTechAPI.METATILEENTITIES[ApeironConfig.getMachineId(13)] == null,
            "retired automatic beam input was still registered");
        Apeiron.LOG.info(
            "Target chamber verification passed: pattern-gated paid particles, native beam fallback, isolated masks, exact outputs and persistence");
    }

    private static void verifyPatternParticles() throws ReflectiveOperationException {
        for (int mode = 0; mode < 3; mode++) {
            Chamber machine = new Chamber();
            machine.addInputBusToMachineList(new Source().getBaseMetaTileEntity(), 0);
            Source source = null;
            if (mode == 0) machine.mInputBusses
                .add(bus(new ItemStack(Items.diamond, 4), new ItemStack(Items.emerald, 2), photon(2)));
            else {
                source = source(new ItemStack(Items.diamond, 4), new ItemStack(Items.emerald, 2), photon(2));
                if (mode == 2) {
                    source = source(new ItemStack(Items.diamond, 2));
                    add(source, 1, new ItemStack(Items.emerald));
                }
                machine.addInputBusToMachineList(source.getBaseMetaTileEntity(), 0);
            }
            MTEInfiniteMEOutputAssembly output = InfiniteMEOutputAssemblySmoke.assembly();
            machine.mOutputBusses.add(output);
            boolean started = ((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe();
            check(started == (mode != 2), "pattern particle mode mixed isolated patterns");
            if (mode == 2) continue;
            check(
                machine.mMaxProgresstime == 1 && machine.lEUt == -1920,
                "pattern particle mode duration or native energy");
            WirelessRecipeState state = ((BigWirelessController) machine).getWirelessRecipeState();
            check(
                state.usesNativeEnergy() && state.getParallelsBig()
                    .equals(BigInteger.valueOf(2)),
                "pattern particle mode batch");
            net.minecraft.nbt.NBTTagCompound saved = new net.minecraft.nbt.NBTTagCompound();
            machine.saveNBTData(saved);
            Chamber restored = new Chamber();
            restored.loadNBTData(saved);
            restored.mOutputBusses.add(output);
            restored.advance();
            check(restored.mProgresstime == 1, "pattern particle mode saved recipe waited for a packet");
            restored.complete();
            BigInteger expected = BigInteger.valueOf(Integer.MAX_VALUE)
                .multiply(BigInteger.valueOf(2));
            check(
                output.getProvider()
                    .getCachedAmountBig()
                    .equals(expected),
                "pattern particle mode output truncated");
            restored.complete();
            check(
                output.getProvider()
                    .getCachedAmountBig()
                    .equals(expected),
                "pattern particle mode duplicate completion");
            if (source != null) check(
                source.getBuffers()
                    .get(0)
                    .getItemAmountBig()
                    .signum() == 0,
                "pattern particle mode debit");
            machine.clearHatches();
            check(machine.mInputBeamline.isEmpty(), "pattern particle mode scan cleanup");
        }
        Chamber full = new Chamber();
        full.addInputBusToMachineList(new Source().getBaseMetaTileEntity(), 0);
        Source ingredients = source(new ItemStack(Items.diamond, 4), new ItemStack(Items.emerald, 2), photon(2));
        full.addInputBusToMachineList(ingredients.getBaseMetaTileEntity(), 0);
        check(
            !((MultiBlockProcessingAccessor) full).apeiron$checkRecipe(),
            "pattern particle mode accepted missing output");
        check(
            ingredients.getBuffers()
                .get(0)
                .getItemAmountBig()
                .equals(BigInteger.valueOf(8)),
            "pattern particle mode lost inputs");

        verifyPatternMasks();
        verifyProgrammedMasks();
        verifyPatternCapacity();
        verifyPaidParticles();
        Apeiron.LOG.info(
            "Pattern particle verification passed: automatic gating, 1 tick, native EU, isolated inputs, masks, paid particles, output protection, reload and exact outputs");
    }

    private static void verifyPatternMasks() throws ReflectiveOperationException {
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
                machine.addInputBusToMachineList(new Source().getBaseMetaTileEntity(), 0);
                machine.mOutputBusses.add(InfiniteMEOutputAssemblySmoke.assembly());
                if (mode == 0) {
                    machine.mask(bus(mask.copy()));
                    machine.mInputBusses.add(bus(new ItemStack(Items.ender_pearl), photon(Integer.MAX_VALUE)));
                } else if (mode == 1) {
                    machine.mask(source(mask.copy()));
                    machine.addInputBusToMachineList(
                        source(new ItemStack(Items.ender_pearl), photon(Integer.MAX_VALUE)).getBaseMetaTileEntity(),
                        0);
                } else if (mode == 2) {
                    machine.mask(bus(new ItemStack(Items.ender_pearl)));
                    machine.mInputBusses.add(bus(mask.copy()));
                } else machine.mInputBusses.add(bus(new ItemStack(Items.ender_pearl)));
                check(
                    ((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe() == (mode < 2),
                    "pattern particle mode mask role " + mode);
                if (mode < 2)
                    check(machine.mMaxProgresstime == 1, "pattern particle mode large photon count was not 1 tick");
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
            machine.addInputBusToMachineList(new Source().getBaseMetaTileEntity(), 0);
            machine.mInputBusses.add(
                bus(new ItemStack(Items.blaze_rod), particle(gtnhlanth.common.beamline.Particle.ELECTRON.getId(), 1)));
            machine.mOutputBusses.add(InfiniteMEOutputAssemblySmoke.assembly());
            check(
                ((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe(),
                "beam conditioner rejected supplied electrons");
        } finally {
            LanthanidesRecipeMaps.targetChamberRecipes.getBackend()
                .removeRecipe(nonPhoton);
        }
    }

    private static void verifyProgrammedMasks() throws ReflectiveOperationException {
        ItemStack mask = new ItemStack(LanthItemList.maskMap.get(MaskList.CSOC), 0);
        ItemStack wrongMask = new ItemStack(LanthItemList.maskMap.get(MaskList.ASOC), 0);
        GTRecipe recipe = GTRecipeBuilder.builder()
            .itemInputs(mask, new ItemStack(Items.quartz))
            .itemOutputs(new ItemStack(Items.apple, 512))
            .metadata(
                LanthanidesRecipeMaps.TARGET_CHAMBER_METADATA,
                TargetChamberMetadata.builder(mask)
                    .particleID(gtnhlanth.common.beamline.Particle.PHOTON.getId())
                    .amount(2)
                    .energy(4, 10, 1)
                    .minFocus(45)
                    .build())
            .eut(1920)
            .duration(1)
            .build()
            .get();
        LanthanidesRecipeMaps.targetChamberRecipes.addRecipe(recipe);
        try {
            for (int mode = 0; mode < 4; mode++) {
                Chamber machine = new Chamber();
                machine.addInputBusToMachineList(new Source().getBaseMetaTileEntity(), 0);
                machine.mask(bus());
                MTEInfiniteMEOutputAssembly output = InfiniteMEOutputAssemblySmoke.assembly();
                machine.mOutputBusses.add(output);
                Source source = new Source();
                BigPatternBuffer buffer = source.getBuffers()
                    .get(0);
                // Mirrors the player's material buffer: zero-count particle and mask selectors plus real material.
                ItemStack particle = new ItemStack(LanthItemList.PARTICLE_ITEM, 0, 1);
                buffer.assign(0, null, Arrays.asList(particle, mode == 1 || mode == 2 ? wrongMask : mask));
                buffer.add(
                    Arrays.asList(AEItemStack.create(new ItemStack(Items.quartz, 2)), AEItemStack.create(photon(4))),
                    BigInteger.ONE);
                if (mode == 2) {
                    BigPatternBuffer other = source.getBuffers()
                        .get(1);
                    other.assign(1, null, Collections.singletonList(mask));
                    other.add(
                        Collections.singletonList(AEItemStack.create(new ItemStack(Items.blaze_powder))),
                        BigInteger.ONE);
                }
                if (mode == 3) buffer.add(
                    Collections
                        .singletonList(AEItemStack.create(new ItemStack(LanthItemList.maskMap.get(MaskList.CSOC)))),
                    BigInteger.ONE);
                machine.addInputBusToMachineList(source.getBaseMetaTileEntity(), 0);
                boolean started = ((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe();
                check(started == (mode == 0 || mode == 3), "programmed mask role or buffer isolation " + mode);
                if (started) {
                    check(
                        buffer.getItemAmountBig()
                            .equals(mode == 3 ? BigInteger.ONE : BigInteger.ZERO),
                        "programmed mask material was not consumed");
                    check(
                        buffer.getSelectors()
                            .size() == 2,
                        "programmed mask or particle selector was consumed");
                    machine.advance();
                    machine.complete();
                    check(
                        output.getProvider()
                            .getCachedAmountBig()
                            .equals(BigInteger.valueOf(1024)),
                        "programmed mask output amount");
                } else check(
                    buffer.getItemAmountBig()
                        .signum() > 0,
                    "rejected mask consumed real material");
            }
        } finally {
            LanthanidesRecipeMaps.targetChamberRecipes.getBackend()
                .removeRecipe(recipe);
        }
    }

    private static void verifyPatternCapacity() {
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
            machine.addInputBusToMachineList(new Source().getBaseMetaTileEntity(), 0);
            ItemStack sticks = new ItemStack(Items.stick, Integer.MAX_VALUE);
            machine.mInputBusses.add(bus(sticks, photon(Integer.MAX_VALUE)));
            gregtech.api.metatileentity.implementations.MTEHatchOutputBus output = new gregtech.api.metatileentity.implementations.MTEHatchOutputBus(
                "apeiron.verify.laser_capacity",
                4,
                new String[0],
                null);
            output.setBaseMetaTileEntity(new BaseMetaTileEntity());
            machine.mOutputBusses.add(output);
            check(
                ((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe(),
                "pattern particle mode capacity search failed");
            BigInteger parallels = ((BigWirelessController) machine).getWirelessRecipeState()
                .getParallelsBig();
            check(
                parallels.signum() > 0 && parallels.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) < 0,
                "pattern particle mode did not limit batch to finite output");
            check(
                sticks.stackSize == Integer.MAX_VALUE - parallels.intValueExact(),
                "pattern particle mode capacity debit");
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
                "pattern particle mode finite output lost items");
        } finally {
            LanthanidesRecipeMaps.targetChamberRecipes.getBackend()
                .removeRecipe(recipe);
        }
    }

    private static void verifyPaidParticles() throws ReflectiveOperationException {
        for (gtnhlanth.common.beamline.Particle type : new gtnhlanth.common.beamline.Particle[] {
            gtnhlanth.common.beamline.Particle.PHOTON, gtnhlanth.common.beamline.Particle.ZBOSON,
            gtnhlanth.common.beamline.Particle.WBOSON, gtnhlanth.common.beamline.Particle.ELECTRON }) {
            GTRecipe recipe = GTRecipeBuilder.builder()
                .itemInputs(new ItemStack(Items.feather))
                .itemOutputs(new ItemStack(Items.apple, 7))
                .metadata(
                    LanthanidesRecipeMaps.TARGET_CHAMBER_METADATA,
                    TargetChamberMetadata.builder(null)
                        .particleID(type.getId())
                        .amount(3)
                        .energy(1_000_000, 2_000_000, 1)
                        .minFocus(100)
                        .build())
                .eut(1920)
                .duration(1)
                .build()
                .get();
            LanthanidesRecipeMaps.targetChamberRecipes.addRecipe(recipe);
            try {
                for (int mode = 0; mode < 8; mode++) {
                    Chamber machine = new Chamber();
                    machine.addInputBusToMachineList(new Source().getBaseMetaTileEntity(), 0);
                    ItemStack material = new ItemStack(Items.feather, 3);
                    int id = mode == 1 ? (type.getId() + 1) % gtnhlanth.common.beamline.Particle.VALUES.length
                        : type.getId();
                    ItemStack particles = particle(id, mode == 0 ? 0 : mode == 2 ? 2 : 7);
                    Source source = null;
                    if (mode == 3 || mode == 4) {
                        source = source(material);
                        if (mode == 3) source.getBuffers()
                            .get(0)
                            .add(Collections.singletonList(AEItemStack.create(particles)), BigInteger.ONE);
                        else add(source, 1, particles);
                        machine.addInputBusToMachineList(source.getBaseMetaTileEntity(), 0);
                    } else machine.mInputBusses.add(bus(material, particles));
                    MTEInfiniteMEOutputAssembly output = InfiniteMEOutputAssemblySmoke.assembly();
                    if (mode != 5) machine.mOutputBusses.add(output);
                    if (mode == 6) machine.voltageTier = 1;
                    boolean expected = mode == 3 || mode == 7;
                    boolean started = ((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe();
                    check(started == expected, "paid particle type/count/voltage/isolation " + type + " mode=" + mode);
                    if (!expected) {
                        check(
                            material.stackSize == 3 && particles.stackSize == (mode == 0 ? 0 : mode == 2 ? 2 : 7),
                            "rejected particle input consumed material or particles");
                        if (source != null) check(
                            source.getBuffers()
                                .get(0)
                                .getItemAmountBig()
                                .equals(BigInteger.valueOf(3)),
                            "isolated particle rejection consumed a pattern");
                        continue;
                    }
                    check(
                        machine.mMaxProgresstime == 1 && machine.lEUt == -1920,
                        "beam conditioner altered machine EU or duration");
                    check(
                        ((BigWirelessController) machine).getWirelessRecipeState()
                            .getParallelsBig()
                            .equals(BigInteger.valueOf(2)),
                        "particle quantity did not limit the batch");
                    if (source != null) check(
                        source.getBuffers()
                            .get(0)
                            .getItemAmountBig()
                            .equals(BigInteger.valueOf(2)),
                        "pattern particle/material debit was not exact");
                    else check(
                        material.stackSize == 1 && particles.stackSize == 1,
                        "particle/material debit was not exact");
                    machine.advance();
                    check(machine.mProgresstime == 1, "paid particle recipe still waited for beam focus or eV");
                    machine.complete();
                    machine.complete();
                    check(
                        output.getProvider()
                            .getCachedAmountBig()
                            .equals(BigInteger.valueOf(14)),
                        "paid particle output was lost or duplicated");
                    check(
                        recipe.mInputs.length == 1 && recipe.mInputs[0].stackSize == 1,
                        "beam conditioner modified the registered recipe");
                }
                Chamber stocked = new Chamber();
                stocked.addInputBusToMachineList(new Source().getBaseMetaTileEntity(), 0);
                stocked.mOutputBusses.add(InfiniteMEOutputAssemblySmoke.assembly());
                com.silvia.apeiron.common.machine.me.stocking.verification.StockingInputsSmoke.verifyCustomItemInputs(
                    stocked,
                    input -> stocked.addInputBusToMachineList(input.getBaseMetaTileEntity(), 0),
                    new ItemStack[] { new ItemStack(Items.feather, 2), particle(type.getId(), 6) });
            } finally {
                LanthanidesRecipeMaps.targetChamberRecipes.getBackend()
                    .removeRecipe(recipe);
            }
        }
    }

    private static void verifyNativeBeam() throws ReflectiveOperationException {
        for (int mode = 0; mode < 5; mode++) {
            Chamber machine = new Chamber();
            ItemStack diamond = new ItemStack(Items.diamond, 2);
            ItemStack emerald = new ItemStack(Items.emerald);
            machine.mInputBusses.add(bus(diamond, emerald, photon(10)));
            machine.mOutputBusses.add(InfiniteMEOutputAssemblySmoke.assembly());
            if (mode > 0) {
                machine.beam();
                machine.mInputBeamline.get(0).dataPacket = new BeamLinePacket(
                    new BeamInformation(mode == 1 ? 1 : 150, 1, mode == 3 ? 1 : 0, mode == 2 ? 1 : 100));
            }
            boolean started = ((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe();
            check(started == (mode == 4), "ordinary input bypassed native beam checks mode=" + mode);
            if (!started)
                check(diamond.stackSize == 2 && emerald.stackSize == 1, "rejected native beam consumed inputs");
        }
    }

    private static void verifyPatternGate() throws ReflectiveOperationException {
        for (int mode = 0; mode < 5; mode++) {
            Chamber machine = new Chamber();
            Source source = new Source();
            MTEHatchInputBus hatch = mode == 0 || mode == 2 ? source : new Mirror(mode == 3 ? null : source);
            if (mode == 2) machine.mask(hatch);
            else machine.addInputBusToMachineList(hatch.getBaseMetaTileEntity(), 0);
            if (mode == 4) ((BaseMetaTileEntity) source.getBaseMetaTileEntity()).invalidate();
            ItemStack photons = photon(1);
            machine.mInputBusses.add(bus(new ItemStack(Items.diamond, 2), new ItemStack(Items.emerald), photons));
            machine.mOutputBusses.add(InfiniteMEOutputAssemblySmoke.assembly());
            boolean started = ((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe();
            check(started == (mode < 3), "target pattern/mirror gate mode=" + mode);
            check(photons.stackSize == (mode < 3 ? 0 : 1), "target pattern/mirror particle debit mode=" + mode);
        }
    }

    private static void verifyRegisteredGTRecipes() throws ReflectiveOperationException {
        int verified = 0;
        for (GTRecipe recipe : LanthanidesRecipeMaps.targetChamberRecipes.getAllRecipes()) {
            TargetChamberMetadata metadata = recipe.getMetadata(LanthanidesRecipeMaps.TARGET_CHAMBER_METADATA);
            if (metadata == null || metadata.focusItem == null || metadata.focusItem.stackSize <= 0) continue;
            if (Arrays.stream(recipe.mInputs)
                .noneMatch(
                    input -> input != null && input.stackSize > 0
                        && input.getItem() instanceof gtnhlanth.common.item.ItemPhotolithographicMask))
                continue;
            for (int mode = 0; mode < 4; mode++) {
                Chamber machine = new Chamber();
                machine.voltageTier = 14;
                Source source = new Source();
                java.util.List<ItemStack> supplied = new ArrayList<>();
                for (ItemStack input : recipe.mInputs) {
                    if (input == null) continue;
                    if (input.getItem() instanceof gtnhlanth.common.item.ItemPhotolithographicMask) {
                        if (mode == 1) {
                            machine.mask(bus(input.copy()));
                            continue;
                        }
                        if (mode == 2) continue;
                    }
                    supplied.add(input.copy());
                }
                supplied.add(particle(metadata.particleID, mode == 3 ? metadata.amount - 1 : metadata.amount));
                add(source, 0, supplied.toArray(new ItemStack[0]));
                machine.addInputBusToMachineList(source.getBaseMetaTileEntity(), 0);
                MTEInfiniteMEOutputAssembly output = InfiniteMEOutputAssemblySmoke.assembly();
                machine.mOutputBusses.add(output);
                BigInteger before = source.getBuffers()
                    .get(0)
                    .getItemAmountBig();
                boolean started = ((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe();
                check(
                    started == (mode < 2),
                    "registered GT target recipe mask/particle mode=" + mode
                        + " output="
                        + recipe.mOutputs[0].getDisplayName());
                if (started) {
                    check(
                        source.getBuffers()
                            .get(0)
                            .getItemAmountBig()
                            .signum() == 0,
                        "registered GT target recipe did not debit full input");
                    machine.advance();
                    machine.complete();
                    check(
                        output.getProvider()
                            .getCachedAmountBig()
                            .equals(BigInteger.valueOf(recipe.mOutputs[0].stackSize)),
                        "registered GT target recipe output changed");
                } else check(
                    source.getBuffers()
                        .get(0)
                        .getItemAmountBig()
                        .equals(before),
                    "rejected GT target recipe lost inputs");
            }
            verified++;
        }
        check(verified > 0, "no registered consumable-mask GT recipes were tested");
        Apeiron.LOG.info(
            "Registered GT target chamber recipes verified: {} recipes, pattern masks, dedicated masks and rejection",
            verified);
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
