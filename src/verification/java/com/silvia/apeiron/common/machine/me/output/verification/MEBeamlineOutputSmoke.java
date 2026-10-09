package com.silvia.apeiron.common.machine.me.output.verification;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.me.output.MTEBeamlineMEOutputHatch;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;

import appeng.api.AEApi;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.common.tileentities.machines.multi.beamcrafting.LHCModule;
import gregtech.common.tileentities.machines.multi.beamcrafting.MTEBeamMultiBase.BeamHatchElement;
import gregtech.common.tileentities.machines.multi.beamcrafting.MTEBeamMultiBase.FundamentalForce;
import gregtech.common.tileentities.machines.multi.beamcrafting.MTELargeHadronCollider;
import gtnhlanth.common.beamline.BeamInformation;
import gtnhlanth.common.beamline.BeamLinePacket;
import gtnhlanth.common.beamline.Particle;
import gtnhlanth.common.hatch.MTEHatchOutputBeamline;
import gtnhlanth.common.register.LanthItemList;
import gtnhlanth.common.tileentity.MTESourceChamber;

/** Real emitters, native structure adders, filtered/disconnected output and drop/reload conservation. */
public final class MEBeamlineOutputSmoke {

    private MEBeamlineOutputSmoke() {}

    private static ApeironMachineTile tile(NBTTagCompound tag) {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(tag, (short) ApeironConfig.getMachineId(ApeironMachines.ME_BEAMLINE_OUTPUT_OFFSET));
        check(tile.getMetaTileEntity() instanceof MTEBeamlineMEOutputHatch, "placed hatch type");
        return tile;
    }

    private static MTEBeamlineMEOutputHatch hatch() {
        return (MTEBeamlineMEOutputHatch) tile(null).getMetaTileEntity();
    }

    public static void verify() {
        try {
            verifySource();
            verifyAdvanced();
            verifyParticles();
            verifyFilterAndDrops();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("ME beamline output fixture", error);
        }
        Apeiron.LOG.info(
            "ME beamline output verification passed: native source/collider emitters, both structure adders, module selection, particle identities, exact counts, filtering, disconnected buffering and drops/reload");
    }

    private static void verifySource() throws ReflectiveOperationException {
        MTESourceChamber source = new MTESourceChamber("apeiron.verify.me_beam_source");
        source.setBaseMetaTileEntity(new BaseMetaTileEntity());
        MTEBeamlineMEOutputHatch hatch = hatch();
        check(source.addBeamLineOutputHatch(hatch.getBaseMetaTileEntity(), 0), "ordinary output registration");
        check(source.addBeamLineOutputHatch(hatch.getBaseMetaTileEntity(), 0), "ordinary repeated registration");
        check(source.mOutputBeamline.size() == 1, "duplicate ordinary output");
        check(source.mOutputBeamline.get(0) == hatch.getBeamOutput(), "native ordinary output view");
        check(
            hatch.getBaseMetaTileEntity()
                .getMetaTileEntity() == hatch,
            "beam view replaced placed hatch");
        check(
            BeamHatchElement.BeamlineOutput.mteClasses()
                .stream()
                .anyMatch(type -> type.isInstance(hatch)),
            "beam structure type recognition");
        set(source, "outputEnergy", 150f);
        set(source, "outputRate", 37);
        set(source, "outputParticle", Particle.ELECTRON.getId());
        set(source, "outputFocus", 100f);
        invoke(source, "outputPacketAfterRecipe", new Class<?>[0]);
        check(hatch.getBeamOutput().dataPacket != null, "source did not emit to ME view");
        hatch.collectBeamOutputs();
        hatch.collectBeamOutputs();
        check(
            hatch.getStoredParticleAmount()
                .equals(BigInteger.valueOf(37)),
            "ordinary packet counted twice");
        check(
            hatch.getProvider()
                .getCachedAmountBig(AEItemStack.create(particle(Particle.ELECTRON)))
                .equals(BigInteger.valueOf(37)),
            "source particle identity");
        source.clearHatches();
        check(source.mOutputBeamline.isEmpty(), "ordinary output scan cleanup");
        MTEHatchOutputBeamline ordinary = new MTEHatchOutputBeamline(
            "apeiron.verify.native_beam_output",
            4,
            new String[0],
            null);
        ordinary.setBaseMetaTileEntity(new BaseMetaTileEntity());
        check(source.addBeamLineOutputHatch(ordinary.getBaseMetaTileEntity(), 0), "native beam output changed");
        invoke(source, "outputPacketAfterRecipe", new Class<?>[0]);
        check(
            ordinary.dataPacket != null && ordinary.dataPacket.getContent()
                .getRate() == 37,
            "native beam packet changed");
    }

    private static void verifyAdvanced() throws ReflectiveOperationException {
        for (FundamentalForce force : FundamentalForce.values()) {
            MTELargeHadronCollider collider = new MTELargeHadronCollider("apeiron.verify.me_beam_collider");
            collider.setBaseMetaTileEntity(new BaseMetaTileEntity());
            MTEBeamlineMEOutputHatch hatch = hatch();
            check(
                collider.addAdvancedBeamlineOutputHatch(hatch.getBaseMetaTileEntity(), 0, force),
                "advanced output registration");
            check(
                collider.addAdvancedBeamlineOutputHatch(hatch.getBaseMetaTileEntity(), 0, force),
                "advanced repeated registration");
            check(collider.mAdvancedOutputBeamline.size() == 1, "duplicate advanced output");
            LHCModule module = force == FundamentalForce.EM ? LHCModule.EM
                : force == FundamentalForce.Weak ? LHCModule.Weak
                    : force == FundamentalForce.Strong ? LHCModule.Strong
                        : force == FundamentalForce.Gravity ? LHCModule.Grav : LHCModule.AllParticles;
            check(
                hatch.getBeamOutput().acceptedInputMap.keySet()
                    .equals(new java.util.HashSet<>(module.acceptedParticles)),
                "native module particle set changed");
            set(collider, "outputEnergy", 1e12f);
            set(collider, "outputFocus", 100f);
            invoke(collider, "outputPacketAfterRecipe", new Class<?>[] { int.class }, 53);
            BeamLinePacket packet = hatch.getBeamOutput().dataPacket;
            check(
                packet != null && module.acceptedParticles.contains(
                    packet.getContent()
                        .getParticle()),
                "collider produced wrong particle for module");
            hatch.collectBeamOutputs();
            check(
                hatch.getStoredParticleAmount()
                    .equals(BigInteger.valueOf(53)),
                "collider output count");
            collider.clearHatches();
            check(collider.mAdvancedOutputBeamline.isEmpty(), "advanced output scan cleanup");
        }
    }

    private static void verifyParticles() {
        MTEBeamlineMEOutputHatch hatch = hatch();
        BigInteger expected = BigInteger.ZERO;
        for (Particle particle : Particle.VALUES) {
            hatch.getBeamOutput().dataPacket = new BeamLinePacket(
                new BeamInformation(100, Integer.MAX_VALUE, particle.getId(), 100));
            hatch.collectBeamOutputs();
            expected = expected.add(BigInteger.valueOf(Integer.MAX_VALUE));
            check(
                hatch.getProvider()
                    .getCachedAmountBig(AEItemStack.create(particle(particle)))
                    .equals(BigInteger.valueOf(Integer.MAX_VALUE)),
                "particle subtype count");
        }
        check(
            hatch.getStoredParticleAmount()
                .equals(expected),
            "aggregate count overflow");
        hatch.getBeamOutput().dataPacket = new BeamLinePacket(new BeamInformation(100, 0, 0, 100));
        hatch.collectBeamOutputs();
        check(
            hatch.getStoredParticleAmount()
                .equals(expected),
            "empty packet produced particles");
        NBTTagCompound saved = new NBTTagCompound();
        hatch.saveNBTData(saved);
        MTEBeamlineMEOutputHatch restored = hatch();
        restored.loadNBTData(saved);
        restored.loadNBTData(saved);
        check(
            restored.getStoredParticleAmount()
                .equals(expected),
            "reload lost or duplicated particles");
        check(
            restored.getBaseMetaTileEntity()
                .getMetaTileEntity() == restored,
            "restored output view replaced tile");
    }

    private static void verifyFilterAndDrops() {
        ApeironMachineTile tile = tile(null);
        MTEBeamlineMEOutputHatch hatch = (MTEBeamlineMEOutputHatch) tile.getMetaTileEntity();
        check(
            !tile.getDrops()
                .get(0)
                .hasTagCompound(),
            "empty hatch cannot stack");
        ItemStack cell = AEApi.instance()
            .definitions()
            .items()
            .cell1k()
            .maybeStack(1)
            .get();
        ((IStorageCell) cell.getItem()).getConfigAEInventory(cell)
            .putAEStackInSlot(0, AEItemStack.create(new ItemStack(Items.diamond)));
        hatch.mInventory[0] = cell;
        hatch.onContentsChanged(0);
        check(
            hatch.getProvider()
                .isFiltered(),
            "filter fixture");
        for (int i = 0; i < 2; i++) {
            hatch.getBeamOutput().dataPacket = new BeamLinePacket(
                new BeamInformation(100, Integer.MAX_VALUE, Particle.PROTON.getId(), 100));
            hatch.collectBeamOutputs();
        }
        BigInteger expected = BigInteger.valueOf(Integer.MAX_VALUE)
            .multiply(BigInteger.valueOf(2));
        check(
            hatch.getPendingParticleAmount()
                .equals(expected)
                && hatch.getProvider()
                    .getCachedAmountBig()
                    .signum() == 0,
            "filter lost particles");
        hatch.getBeamOutput().dataPacket = new BeamLinePacket(
            new BeamInformation(100, 97, Particle.NEUTRON.getId(), 100));
        expected = expected.add(BigInteger.valueOf(97));
        ItemStack dropped = tile.getDrops()
            .get(0);
        check(dropped.getItem() == Item.getItemFromBlock(ApeironMachines.block), "drop registry");
        check(
            dropped.hasTagCompound() && dropped.getTagCompound()
                .hasKey(MTEBeamlineMEOutputHatch.PENDING_TAG),
            "drop lost filtered queue");
        MTEBeamlineMEOutputHatch replaced = (MTEBeamlineMEOutputHatch) tile(dropped.getTagCompound())
            .getMetaTileEntity();
        check(
            replaced.getStoredParticleAmount()
                .equals(expected),
            "drop/replacement conservation");
        check(replaced.mInventory[0] != null, "drop lost storage cell");
        replaced.mInventory[0] = null;
        replaced.onContentsChanged(0);
        replaced.collectBeamOutputs();
        check(
            replaced.getPendingParticleAmount()
                .signum() == 0 && replaced.getProvider()
                    .getCachedAmountBig()
                    .equals(expected),
            "cleared filter failed to release queue");
        List<IAEItemStack> cache = replaced.getProvider()
            .getCacheList();
        check(
            cache.size() == 2 && cache.stream()
                .allMatch(stack -> stack.getItem() == LanthItemList.PARTICLE_ITEM),
            "ME contents are not native particle items");
        replaced.collectBeamOutputs();
        check(
            replaced.getStoredParticleAmount()
                .equals(expected),
            "repeated collection duplicated output");
    }

    private static ItemStack particle(Particle particle) {
        return new ItemStack(LanthItemList.PARTICLE_ITEM, 1, particle.getId());
    }

    private static void set(Object instance, String name, Object value) throws ReflectiveOperationException {
        Field field = instance.getClass()
            .getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static void invoke(Object instance, String name, Class<?>[] types, Object... args)
        throws ReflectiveOperationException {
        Method method = instance.getClass()
            .getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(instance, args);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException("ME beamline output: " + message);
    }
}
