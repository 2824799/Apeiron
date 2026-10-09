package com.silvia.apeiron.common.machine.me.output;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTSplit;
import gregtech.common.tileentities.machines.multi.beamcrafting.MTEHatchAdvancedOutputBeamline;
import gtnhlanth.common.beamline.BeamInformation;
import gtnhlanth.common.beamline.Particle;
import gtnhlanth.common.register.LanthItemList;

/** One ME node; the native beamline output view shares the placed hatch and produces ordinary particle items. */
@IMetaTileEntity.SkipGenerateDescription
@IMetaTileEntity.SkipGenerateName
public class MTEBeamlineMEOutputHatch extends MTEBoundlessMEOutputBus {

    public static final String PENDING_TAG = "ApeironBeamlineOutputs";
    private final BigMachineOutputQueue pending = new BigMachineOutputQueue();
    private final BeamPort beamPort = new BeamPort();

    public MTEBeamlineMEOutputHatch(int id, String name, String regionalName) {
        super(id, name, regionalName);
    }

    private MTEBeamlineMEOutputHatch(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, description, textures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEBeamlineMEOutputHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public String getLocalName() {
        return StatCollector.translateToLocal("gt.blockmachines.apeiron.me_beamline_output_hatch.name");
    }

    @Override
    public String[] getDescription() {
        return GTSplit.splitLocalized("apeiron.machine.me_beamline_output_hatch.desc");
    }

    public MTEHatchAdvancedOutputBeamline getBeamOutput() {
        return beamPort;
    }

    public BigInteger getPendingParticleAmount() {
        BigInteger amount = pending.getItemAmountBig();
        if (beamPort.dataPacket != null) amount = amount.add(
            BigInteger.valueOf(
                Math.max(
                    0,
                    beamPort.dataPacket.getContent()
                        .getRate())));
        return amount;
    }

    public BigInteger getStoredParticleAmount() {
        return getPendingParticleAmount().add(getProvider().getCachedAmountBig());
    }

    private void captureBeam() {
        if (beamPort.dataPacket == null) return;
        BeamInformation beam = beamPort.dataPacket.getContent();
        if (beam.getRate() > 0) pending.addItem(
            new ItemStack(LanthItemList.PARTICLE_ITEM, 1, beam.getParticleId()),
            BigInteger.valueOf(beam.getRate()));
        beamPort.dataPacket = null;
        markDirty();
    }

    /** Capture once, then retry rejected particles without retaining a live native beam packet. */
    public void collectBeamOutputs() {
        captureBeam();
        if (!pending.isEmpty() && pending.flush(Collections.singletonList(this), Collections.emptyList(), true, true))
            markDirty();
    }

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        if (tile.isServerSide()) collectBeamOutputs();
        super.onPostTick(tile, tick);
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        captureBeam();
        super.saveNBTData(tag);
        savePending(tag);
    }

    @Override
    public void setItemNBT(NBTTagCompound tag) {
        captureBeam();
        super.setItemNBT(tag);
        savePending(tag);
    }

    private void savePending(NBTTagCompound tag) {
        NBTTagCompound state = new NBTTagCompound();
        pending.save(state);
        if (!state.hasNoTags()) tag.setTag(PENDING_TAG, state);
        else tag.removeTag(PENDING_TAG);
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        beamPort.dataPacket = null;
        pending.load(tag.getCompoundTag(PENDING_TAG));
    }

    @Override
    public void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {
        super.addAdditionalTooltipInformation(stack, tooltip);
        BigMachineOutputQueue saved = new BigMachineOutputQueue();
        if (stack.hasTagCompound()) saved.load(
            stack.getTagCompound()
                .getCompoundTag(PENDING_TAG));
        if (!saved.isEmpty()) tooltip.add(
            StatCollector.translateToLocalFormatted(
                "apeiron.machine.me_beamline_output_hatch.pending",
                com.silvia.apeiron.math.BigNumberFormatter.formatExact(saved.getItemAmountBig())));
    }

    @Override
    public String[] getInfoData() {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>(Arrays.asList(super.getInfoData()));
        if (getPendingParticleAmount().signum() > 0) lines.add(
            gregtech.api.interfaces.tileentity.IGregTechDeviceInformation.encode(
                "apeiron.machine.me_beamline_output_hatch.pending",
                com.silvia.apeiron.math.BigNumberFormatter.formatExact(getPendingParticleAmount())));
        return lines.toArray(new String[0]);
    }

    private final class BeamPort extends MTEHatchAdvancedOutputBeamline {

        private BeamPort() {
            super("apeiron.me_beamline_output_view", 4, new String[0], null);
            for (Particle particle : Particle.VALUES) acceptedInputMap.put(particle, true);
            // MetaTileEntity's final setter assigns the tile's MTE; a delegated view prevents it
            // from replacing the real hatch while preserving native validity checks and coordinates.
            IGregTechTileEntity view = (IGregTechTileEntity) Proxy.newProxyInstance(
                IGregTechTileEntity.class.getClassLoader(),
                new Class<?>[] { IGregTechTileEntity.class },
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        if (method.getName()
                            .equals("equals")) return proxy == args[0];
                        if (method.getName()
                            .equals("hashCode")) return System.identityHashCode(proxy);
                        if (method.getName()
                            .equals("toString")) return "Apeiron beamline output view";
                    }
                    if (method.getName()
                        .equals("getMetaTileEntity")) return this;
                    if (method.getName()
                        .equals("setMetaTileEntity")) return null;
                    IGregTechTileEntity base = MTEBeamlineMEOutputHatch.this.getBaseMetaTileEntity();
                    if (base == null) {
                        if (method.getName()
                            .equals("isDead")) return true;
                        throw new IllegalStateException("Beamline output is not attached to a tile");
                    }
                    try {
                        return method.invoke(base, args);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                });
            setBaseMetaTileEntity(view);
        }

        @Override
        public boolean isValid() {
            return MTEBeamlineMEOutputHatch.this.isValid();
        }

        @Override
        public void moveAround(IGregTechTileEntity tile) {
            collectBeamOutputs();
        }
    }
}
