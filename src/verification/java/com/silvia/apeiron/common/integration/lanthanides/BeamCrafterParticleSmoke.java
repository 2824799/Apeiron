package com.silvia.apeiron.common.integration.lanthanides;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
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
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputMirror;
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
import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;
import gregtech.common.tileentities.machines.multi.beamcrafting.MTEBeamCrafter;
import gregtech.loaders.postload.recipes.beamcrafter.BeamCrafterMetadata;
import gtnhlanth.common.beamline.Particle;
import gtnhlanth.common.register.LanthItemList;

/** Real processing logic verifies gate selection, atomic particle payment and native fallback. */
public final class BeamCrafterParticleSmoke {

    private BeamCrafterParticleSmoke() {}

    private static class Crafter extends MTEBeamCrafter {

        private Crafter() {
            super("apeiron.verify.beam_crafter");
            setBaseMetaTileEntity(new BaseMetaTileEntity());
            mWrench = mScrewdriver = mSoftMallet = mHardHammer = mSolderingTool = mCrowbar = true;
        }

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

        private void advance() {
            incrementProgressTime();
        }

        private void complete() {
            outputAfterRecipe();
        }
    }

    private static final class Source extends MTEInfinitePatternInputAssembly {

        private Source() {
            super("apeiron.verify.beam_source", 10, new String[0], null);
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
            super("apeiron.verify.beam_mirror", 10, new String[0], null);
            this.source = source;
            setBaseMetaTileEntity(new BaseMetaTileEntity());
            if (source != null) setLink(0, 10, 20, 30);
        }

        @Override
        protected MTEInfinitePatternInputAssembly lookupSource() {
            return source;
        }
    }

    private static void add(Source source, int index, ItemStack... items) {
        BigPatternBuffer buffer = source.getBuffers()
            .get(index);
        buffer.assign(index, null, Collections.emptyList());
        buffer.add(
            Arrays.stream(items)
                .map(AEItemStack::create)
                .collect(java.util.stream.Collectors.toList()),
            BigInteger.ONE);
    }

    private static ItemStack particle(int id, int amount) {
        return new ItemStack(LanthItemList.PARTICLE_ITEM, amount, id);
    }

    private static MTEHatchInputBus bus(ItemStack... items) {
        MTEHatchInputBus bus = new MTEHatchInputBus("apeiron.verify.beam_bus", 4, new String[0], null);
        bus.setBaseMetaTileEntity(new BaseMetaTileEntity());
        for (int i = 0; i < items.length; i++) bus.setInventorySlotContents(i, items[i]);
        return bus;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException("Beam crafter verification: " + message);
    }

    public static void verify() {
        HashMap<UUID, BigInteger> oldEnergy = GlobalVariableStorage.GlobalEnergy;
        Map<UUID, UUID> oldTeams = SpaceProjectManager.spaceTeams;
        GlobalEnergyWorldSavedData oldSave = GlobalEnergyWorldSavedData.INSTANCE;
        try {
            GlobalVariableStorage.GlobalEnergy = new HashMap<>();
            SpaceProjectManager.spaceTeams = new HashMap<>();
            GlobalEnergyWorldSavedData.INSTANCE = new GlobalEnergyWorldSavedData();
            verifyRecipes();
        } finally {
            GlobalVariableStorage.GlobalEnergy = oldEnergy;
            SpaceProjectManager.spaceTeams = oldTeams;
            GlobalEnergyWorldSavedData.INSTANCE = oldSave;
        }
    }

    private static void verifyRecipes() {
        for (boolean sameParticle : new boolean[] { false, true }) {
            int a = Particle.PHOTON.getId();
            int b = sameParticle ? a : Particle.ELECTRON.getId();
            GTRecipe recipe = GTRecipeBuilder.builder()
                .itemInputs(new ItemStack(Items.feather))
                .itemOutputs(new ItemStack(Items.apple))
                .metadata(
                    RecipeMaps.BEAMCRAFTER_METADATA,
                    BeamCrafterMetadata.builder()
                        .particleID_A(a)
                        .amount_A(2)
                        .particleID_B(b)
                        .amount_B(3)
                        .build())
                .eut(1920)
                .duration(20)
                .build()
                .get();
            RecipeMaps.beamcrafterRecipes.addRecipe(recipe);
            try {
                verifyEnergyParallel(recipe, sameParticle);
                verifyEnergyRejections(sameParticle);
                for (int mode = 0; mode < 8; mode++) {
                    Crafter machine = new Crafter();
                    machine.mOutputBusses.add(InfiniteMEOutputAssemblySmoke.assembly());
                    ItemStack material = new ItemStack(Items.feather, 2);
                    ItemStack first = particle(a, sameParticle ? 11 : 5);
                    ItemStack second = particle(b, 7);
                    Source source = new Source();
                    if (mode == 0 || mode == 1 || mode == 7) {
                        add(source, 0, material, first);
                        if (!sameParticle) addParticles(source, 0, second);
                        if (mode == 7) {
                            source = new Source();
                            add(source, 0, material);
                            add(source, 1, first, second);
                        }
                        machine.addInputBusToMachineList(
                            (mode == 1 ? new Mirror(source) : source).getBaseMetaTileEntity(),
                            0);
                    } else {
                        if (mode == 2) machine.addInputBusToMachineList(new Mirror(null).getBaseMetaTileEntity(), 0);
                        if (mode >= 4) machine.addInputBusToMachineList(source.getBaseMetaTileEntity(), 0);
                        if (mode == 4) first.stackSize = second.stackSize = 0;
                        if (mode == 5) first.stackSize = sameParticle ? 4 : 1;
                        if (mode == 6) first = particle(Particle.ZBOSON.getId(), first.stackSize);
                        machine.mInputBusses.add(sameParticle ? bus(material, first) : bus(material, first, second));
                    }
                    boolean nativeMode = mode == 2 || mode == 3;
                    boolean paidMode = mode == 0 || mode == 1;
                    boolean started = ((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe();
                    check(started == (nativeMode || paidMode), "gate/quantity/isolation mode=" + mode);
                    if (!started) {
                        check(material.stackSize == 2, "rejected input consumed material");
                        check(
                            source.getBuffers()
                                .get(0)
                                .getItemAmountBig()
                                .equals(mode == 7 ? BigInteger.valueOf(2) : BigInteger.ZERO),
                            "rejected input debited buffer");
                        continue;
                    }
                    if (nativeMode) {
                        check(first.stackSize == (sameParticle ? 11 : 5), "ordinary input consumed particle items");
                        machine.advance();
                        check(machine.mProgresstime == 0, "ordinary input skipped native beam payment");
                        machine.bufferMap.put(a, sameParticle ? 10 : 4);
                        if (!sameParticle) machine.bufferMap.put(b, 6);
                        machine.advance();
                        check(machine.mProgresstime == machine.mMaxProgresstime, "native beams failed to complete");
                    } else {
                        check(
                            source.getBuffers()
                                .get(0)
                                .getItemAmountBig()
                                .equals(BigInteger.valueOf(sameParticle ? 1 : 2)),
                            "particle debit was not exact");
                        NBTTagCompound tag = new NBTTagCompound();
                        machine.saveNBTData(tag);
                        check(tag.getBoolean("ApeironBeamItemRecipe"), "paid mode was not persisted");
                        Crafter restored = new Crafter();
                        restored.loadNBTData(tag);
                        restored.advance();
                        check(restored.mProgresstime == restored.mMaxProgresstime, "paid reload waited for beams");
                        // A later native recipe must clear the paid-mode flag.
                        machine.clearHatches();
                        machine.mInputBusses.add(bus(new ItemStack(Items.feather)));
                        machine.mOutputBusses.add(InfiniteMEOutputAssemblySmoke.assembly());
                        machine.mMaxProgresstime = 0;
                        check(
                            ((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe(),
                            "later native recipe failed");
                        NBTTagCompound nativeTag = new NBTTagCompound();
                        machine.saveNBTData(nativeTag);
                        check(!nativeTag.getBoolean("ApeironBeamItemRecipe"), "later native recipe retained paid flag");
                    }
                }
            } finally {
                RecipeMaps.beamcrafterRecipes.getBackend()
                    .removeRecipe(recipe);
            }
        }
        Apeiron.LOG.info(
            "Beam crafter verification passed: pattern gate, mirrors, exact particle payment, native fallback and reload");
    }

    private static void addParticles(Source source, int index, ItemStack stack) {
        source.getBuffers()
            .get(index)
            .add(Collections.singletonList(AEItemStack.create(stack)), BigInteger.ONE);
    }

    private static MTEInfiniteEnergyHatch attachEnergy(Crafter machine, boolean ultimate) {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(
            null,
            (short) ApeironConfig.getMachineId(
                ultimate ? ApeironMachines.ULTIMATE_ENERGY_HATCH_OFFSET
                    : ApeironMachines.INFINITE_ENERGY_HATCH_OFFSET));
        UUID owner = UUID.randomUUID();
        tile.setOwnerUuid(owner);
        WirelessNetworkManager.setUserEU(owner, BigInteger.TEN.pow(100));
        MTEInfiniteEnergyHatch hatch = (MTEInfiniteEnergyHatch) tile.getMetaTileEntity();
        machine.mEnergyHatches.add(hatch);
        return hatch;
    }

    private static void verifyEnergyParallel(GTRecipe recipe, boolean sameParticle) {
        BigInteger huge = BigInteger.TEN.pow(40)
            .add(BigInteger.valueOf(7));
        for (boolean ultimate : new boolean[] { false, true }) {
            for (BigInteger setting : new BigInteger[] { BigInteger.ONE, BigInteger.valueOf(4096), huge,
                BigInteger.ZERO }) {
                Crafter machine = new Crafter();
                MTEInfiniteEnergyHatch hatch = attachEnergy(machine, ultimate);
                MTEInfiniteMEOutputAssembly output = InfiniteMEOutputAssemblySmoke.assembly();
                machine.mOutputBusses.add(output);
                Source source = new Source();
                BigPatternBuffer buffer = source.getBuffers()
                    .get(0);
                buffer.assign(0, null, Collections.emptyList());
                buffer.add(
                    Arrays.asList(
                        BigAEStackValues.copyWithSize(AEItemStack.create(new ItemStack(Items.feather)), huge),
                        BigAEStackValues.copyWithSize(
                            AEItemStack.create(particle(Particle.PHOTON.getId(), 1)),
                            huge.multiply(BigInteger.valueOf(sameParticle ? 5 : 2))),
                        BigAEStackValues.copyWithSize(
                            AEItemStack.create(
                                particle(sameParticle ? Particle.PHOTON.getId() : Particle.ELECTRON.getId(), 1)),
                            sameParticle ? BigInteger.ZERO : huge.multiply(BigInteger.valueOf(3)))),
                    BigInteger.ONE);
                machine.addInputBusToMachineList(new Mirror(source).getBaseMetaTileEntity(), 0);
                WirelessRecipeState state = ((BigWirelessController) machine).getWirelessRecipeState();
                state.setParallelSettingBig(setting);
                state.setVoltageSetting(1920);
                state.setTargetDuration(7);
                BigInteger before = hatch.getAvailableEUBig();
                check(PatternParticleInputs.hasPatternInput(machine.mDualInputHatches), "energy fixture gate failed");
                boolean started = ((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe();
                NBTTagCompound diagnostic = new NBTTagCompound();
                machine.saveNBTData(diagnostic);
                check(
                    started,
                    "energy particle recipe did not start: ultimate=" + ultimate
                        + " same="
                        + sameParticle
                        + " setting="
                        + setting
                        + " result="
                        + diagnostic.getString("checkRecipeResultID")
                        + " details="
                        + diagnostic.getCompoundTag("checkRecipeResult"));
                BigInteger expected = setting.signum() == 0 ? huge : setting;
                check(
                    state.getParallelsBig()
                        .equals(expected),
                    "energy parallel setting ignored: " + setting);
                check(
                    machine.getCurrentRecipeParticleIDA() == Particle.PHOTON.getId()
                        && machine.getCurrentRecipeParticleIDB()
                            == (sameParticle ? Particle.PHOTON.getId() : Particle.ELECTRON.getId()),
                    "exact particle recipe display retained old particle IDs");
                check(
                    buffer.getItemAmountBig()
                        .equals(
                            huge.subtract(expected)
                                .multiply(BigInteger.valueOf(6))),
                    "big particle/material debit was not exact");
                check(recipe.mInputs.length == 1, "shared beam recipe was mutated");
                check(machine.mMaxProgresstime == (ultimate ? 7 : 1), "wireless work duration ignored");
                BigInteger expectedEnergy = expected.multiply(BigInteger.valueOf(1920));
                check(
                    state.getTotalEUBig()
                        .equals(expectedEnergy),
                    "wireless particle energy cost changed");
                NBTTagCompound saved = new NBTTagCompound();
                machine.saveNBTData(saved);
                Crafter restored = new Crafter();
                restored.loadNBTData(saved);
                restored.mEnergyHatches.add(hatch);
                restored.mOutputBusses.add(output);
                for (int tick = 0; tick < restored.mMaxProgresstime; tick++) {
                    check(restored.onRunningTick(null), "wireless recipe tick failed");
                    restored.advance();
                    check(restored.mProgresstime == tick + 1, "paid particles skipped configured work ticks");
                }
                check(
                    before.subtract(hatch.getAvailableEUBig())
                        .equals(expectedEnergy),
                    "wireless EU debit was not exact");
                restored.complete();
                restored.complete();
                check(
                    output.getProvider()
                        .getCachedAmountBig()
                        .equals(expected),
                    "big particle output lost or duplicated");
            }
        }
        // Native beam input still uses native progress, but the installed energy setting bounds its batch.
        for (int parallel : new int[] { 1, 2048 }) {
            Crafter machine = new Crafter();
            attachEnergy(machine, false);
            ((BigWirelessController) machine).getWirelessRecipeState()
                .setParallelSettingBig(BigInteger.valueOf(parallel));
            machine.mInputBusses.add(bus(new ItemStack(Items.feather, 4096)));
            machine.mOutputBusses.add(InfiniteMEOutputAssemblySmoke.assembly());
            machine.bufferMap.put(Particle.PHOTON.getId(), sameParticle ? 5 * 4096 : 2 * 4096);
            if (!sameParticle) machine.bufferMap.put(Particle.ELECTRON.getId(), 3 * 4096);
            check(((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe(), "native energy recipe did not start");
            check(
                ((MultiBlockProcessingAccessor) machine).apeiron$getProcessingLogic()
                    .getCurrentParallels() == parallel,
                "native beam energy setting ignored");
            machine.advance();
            check(machine.mProgresstime == machine.mMaxProgresstime, "native beam progress changed");
        }
    }

    private static void verifyEnergyRejections(boolean sameParticle) {
        for (boolean ultimate : new boolean[] { false, true }) {
            for (int mode = 0; mode < 7; mode++) {
                Crafter machine = new Crafter();
                MTEInfiniteEnergyHatch hatch = attachEnergy(machine, ultimate);
                WirelessRecipeState state = ((BigWirelessController) machine).getWirelessRecipeState();
                state.setParallelSettingBig(BigInteger.valueOf(4096));
                state.setVoltageSetting(mode == 5 ? 8 : 1920);
                if (ultimate && mode == 5) continue;
                if (mode != 4) machine.mOutputBusses.add(InfiniteMEOutputAssemblySmoke.assembly());
                Source source = new Source();
                ItemStack first = particle(
                    mode == 1 ? Particle.ZBOSON.getId() : Particle.PHOTON.getId(),
                    mode == 0 ? 0 : mode == 2 ? 1 : sameParticle ? 5 : 2);
                ItemStack second = particle(Particle.ELECTRON.getId(), 3);
                add(source, 0, new ItemStack(Items.feather));
                if (mode == 3) add(source, 1, first);
                else addParticles(source, 0, first);
                if (!sameParticle && mode != 0) addParticles(source, mode == 3 ? 1 : 0, second);
                machine.addInputBusToMachineList(source.getBaseMetaTileEntity(), 0);
                if (mode == 6) WirelessNetworkManager.setUserEU(hatch.getOwnerUuid(), BigInteger.ZERO);
                BigInteger before = source.getBuffers()
                    .get(0)
                    .getItemAmountBig();
                BigInteger energyBefore = hatch.getAvailableEUBig();
                check(
                    !((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe(),
                    "wireless invalid input accepted mode=" + mode);
                check(
                    source.getBuffers()
                        .get(0)
                        .getItemAmountBig()
                        .equals(before),
                    "wireless rejection consumed input mode=" + mode);
                check(
                    !state.isRunning() && hatch.getAvailableEUBig()
                        .equals(energyBefore),
                    "wireless rejection charged EU or started mode=" + mode);
            }
        }
    }
}
