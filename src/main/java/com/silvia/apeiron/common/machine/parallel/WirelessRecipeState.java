package com.silvia.apeiron.common.machine.parallel;

import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;

import com.silvia.apeiron.api.machine.parallel.ParallelLimit;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigValueCodec;

/** A finite recipe remains intact when wireless EU is unavailable. No energy is prefetched or reserved. */
public final class WirelessRecipeState {

    private BigInteger parallelSetting = BigInteger.ZERO;
    private BigInteger parallels = BigInteger.ZERO;
    private BigInteger euPerTick = BigInteger.ZERO;
    private int duration;
    private boolean running;
    private BigRecipeParallelHelper prepared;
    private int preparedDuration;
    private BigMachineOutputQueue recipeOutputs = new BigMachineOutputQueue();
    private java.util.List<appeng.api.storage.data.IAEStack<?>> hudOutputs;
    private final BigMachineOutputQueue pendingOutputs = new BigMachineOutputQueue();

    public BigInteger getParallelSettingBig() {
        return parallelSetting;
    }

    public void setParallelSettingBig(BigInteger value) {
        if (value.signum() < 0) throw new IllegalArgumentException("Negative parallel setting");
        parallelSetting = value;
    }

    public ParallelLimit getLimit() {
        return parallelSetting.signum() == 0 ? ParallelLimit.unlimited() : ParallelLimit.bounded(parallelSetting);
    }

    public BigInteger getParallelsBig() {
        return parallels;
    }

    public BigInteger getEUtBig() {
        return euPerTick;
    }

    public BigInteger getTotalEUBig() {
        return euPerTick.multiply(BigInteger.valueOf(duration));
    }

    public boolean isRunning() {
        return running;
    }

    public int getDuration() {
        return duration;
    }

    public java.util.List<appeng.api.storage.data.IAEStack<?>> getOutputDisplay() {
        return running ? recipeOutputs.snapshotOutputs() : java.util.Collections.emptyList();
    }

    public NBTTagCompound writeDisplayNBT() {
        final NBTTagCompound tag = new NBTTagCompound();
        if (running) recipeOutputs.save(tag);
        tag.setInteger("duration", duration);
        tag.setBoolean("running", running);
        return tag;
    }

    public java.util.List<appeng.api.storage.data.IAEStack<?>> getHudOutputs() {
        if (!running) return java.util.Collections.emptyList();
        if (hudOutputs == null) hudOutputs = recipeOutputs.previewOutputs(3);
        return hudOutputs;
    }

    public int getOutputTypeCount() {
        return running ? recipeOutputs.outputTypes() : 0;
    }

    public BigMachineOutputQueue pending() {
        return pendingOutputs;
    }

    public boolean hasPreparedRecipe() {
        return prepared != null;
    }

    public void discardPreparedRecipe() {
        prepared = null;
    }

    public void prepare(BigRecipeParallelHelper helper, int duration) {
        prepared = helper;
        preparedDuration = duration;
    }

    public void commitPreparedRecipe() {
        if (prepared == null) return;
        BigRecipeParallelHelper helper = prepared;
        prepared = null;
        helper.commit(preparedDuration);
    }

    public void start(BigInteger parallels, BigInteger eut, int duration, BigMachineOutputQueue outputs) {
        if (running || parallels.signum() <= 0 || eut.signum() < 0 || duration < 1)
            throw new IllegalStateException("Invalid wireless recipe");
        this.parallels = parallels;
        this.euPerTick = eut;
        this.duration = duration;
        this.recipeOutputs = outputs;
        this.hudOutputs = null;
        running = true;
    }

    public void complete() {
        if (!running) return;
        recipeOutputs.moveTo(pendingOutputs);
        running = false;
    }

    public void cancelRecipe() {
        discardPreparedRecipe();
        running = false;
        recipeOutputs = new BigMachineOutputQueue();
        parallels = BigInteger.ZERO;
        euPerTick = BigInteger.ZERO;
        duration = 0;
    }

    public NBTTagCompound save() {
        NBTTagCompound tag = new NBTTagCompound();
        BigValueCodec.writeNBT(tag, "parallelSetting", "parallelSettingBig", new AdaptiveInteger(parallelSetting));
        BigValueCodec.writeNBT(tag, "parallels", "parallelsBig", new AdaptiveInteger(parallels));
        BigValueCodec.writeNBT(tag, "eut", "eutBig", new AdaptiveInteger(euPerTick));
        tag.setInteger("duration", duration);
        tag.setBoolean("running", running);
        NBTTagCompound recipe = new NBTTagCompound();
        recipeOutputs.save(recipe);
        tag.setTag("recipe", recipe);
        NBTTagCompound pending = new NBTTagCompound();
        pendingOutputs.save(pending);
        tag.setTag("pending", pending);
        return tag;
    }

    /** Only already-produced outputs survive harvesting, matching GT's cancellation of the active recipe. */
    public NBTTagCompound saveProduced() {
        NBTTagCompound tag = save();
        tag.setBoolean("running", false);
        tag.setTag("recipe", new NBTTagCompound());
        return tag;
    }

    public void load(NBTTagCompound tag) {
        hudOutputs = null;
        discardPreparedRecipe();
        parallelSetting = BigValueCodec.readNBT(tag, "parallelSetting", "parallelSettingBig")
            .toBigInteger()
            .max(BigInteger.ZERO);
        parallels = BigValueCodec.readNBT(tag, "parallels", "parallelsBig")
            .toBigInteger()
            .max(BigInteger.ZERO);
        euPerTick = BigValueCodec.readNBT(tag, "eut", "eutBig")
            .toBigInteger()
            .max(BigInteger.ZERO);
        duration = tag.getInteger("duration");
        running = tag.getBoolean("running") && duration > 0 && parallels.signum() > 0;
        recipeOutputs.load(tag.getCompoundTag("recipe"));
        pendingOutputs.load(tag.getCompoundTag("pending"));
    }
}
