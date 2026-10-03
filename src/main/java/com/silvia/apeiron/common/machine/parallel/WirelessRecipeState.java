package com.silvia.apeiron.common.machine.parallel;

import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;

import com.silvia.apeiron.api.machine.parallel.ParallelLimit;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigValueCodec;

/** A finite recipe remains intact when wireless EU is unavailable. No energy is prefetched or reserved. */
public final class WirelessRecipeState {

    private BigInteger parallelSetting = BigInteger.valueOf(Integer.MAX_VALUE);
    private long voltageSetting = Integer.MAX_VALUE;
    private int targetDuration = 128;
    private boolean lossless;
    private boolean exactDebit;
    private BigInteger totalEnergy = BigInteger.ZERO;
    private int paidTicks;
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
        return exactDebit ? totalEnergy : euPerTick.multiply(BigInteger.valueOf(duration));
    }

    public long getVoltageSetting() {
        return voltageSetting;
    }

    public void setVoltageSetting(long voltage) {
        if (voltage < 1) throw new IllegalArgumentException("Voltage must be positive");
        voltageSetting = voltage;
    }

    public int getTargetDuration() {
        return targetDuration;
    }

    public void setTargetDuration(int ticks) {
        if (ticks < 1) throw new IllegalArgumentException("Duration must be positive");
        targetDuration = ticks;
    }

    public boolean isLossless() {
        return lossless;
    }

    public BigInteger nextDebit(int efficiency) {
        return exactDebit ? com.silvia.apeiron.math.RecipeEnergyBudget.tickCost(totalEnergy, duration, paidTicks)
            : com.silvia.apeiron.math.RecipeDisplayNumbers.effectiveEUt(euPerTick, efficiency);
    }

    public void paidTick() {
        if (paidTicks < duration) paidTicks++;
    }

    public BigInteger displayEUt(int efficiency) {
        return exactDebit ? euPerTick
            : com.silvia.apeiron.math.RecipeDisplayNumbers.effectiveEUt(euPerTick, efficiency);
    }

    public BigInteger displayTotalEU(int efficiency) {
        return exactDebit ? totalEnergy : displayEUt(efficiency).multiply(BigInteger.valueOf(duration));
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
        this.lossless = false;
        this.exactDebit = false;
        this.paidTicks = 0;
        this.totalEnergy = eut.multiply(BigInteger.valueOf(duration));
        running = true;
    }

    public void startLossless(BigInteger parallels, BigInteger total, int duration, BigMachineOutputQueue outputs) {
        startExact(parallels, total, duration, outputs, true);
    }

    public void startExact(BigInteger parallels, BigInteger total, int duration, BigMachineOutputQueue outputs,
        boolean lossless) {
        if (running || parallels.signum() <= 0 || total.signum() < 0 || duration < 1)
            throw new IllegalArgumentException("Invalid exact energy schedule");
        this.parallels = parallels;
        this.euPerTick = total.add(BigInteger.valueOf(duration - 1L))
            .divide(BigInteger.valueOf(duration));
        this.duration = duration;
        this.recipeOutputs = outputs;
        this.hudOutputs = null;
        this.lossless = lossless;
        this.exactDebit = true;
        this.paidTicks = 0;
        this.totalEnergy = total;
        this.running = true;
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
        paidTicks = 0;
        lossless = false;
        exactDebit = false;
        totalEnergy = BigInteger.ZERO;
    }

    public NBTTagCompound save() {
        NBTTagCompound tag = new NBTTagCompound();
        BigValueCodec.writeNBT(tag, "parallelSetting", "parallelSettingBig", new AdaptiveInteger(parallelSetting));
        BigValueCodec.writeNBT(tag, "parallels", "parallelsBig", new AdaptiveInteger(parallels));
        BigValueCodec.writeNBT(tag, "eut", "eutBig", new AdaptiveInteger(euPerTick));
        tag.setInteger("duration", duration);
        tag.setBoolean("running", running);
        tag.setLong("voltageSetting", voltageSetting);
        tag.setInteger("settingsVersion", 1);
        tag.setInteger("targetDuration", targetDuration);
        tag.setBoolean("lossless", lossless);
        tag.setBoolean("exactDebit", exactDebit);
        tag.setInteger("paidTicks", paidTicks);
        BigValueCodec.writeNBT(tag, "totalEnergy", "totalEnergyBig", new AdaptiveInteger(totalEnergy));
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
        parallelSetting = tag.hasKey("parallelSetting") || tag.hasKey("parallelSettingBig")
            ? BigValueCodec.readNBT(tag, "parallelSetting", "parallelSettingBig")
                .toBigInteger()
                .max(BigInteger.ZERO)
            : BigInteger.valueOf(Integer.MAX_VALUE);
        // Old saves initialized every controller to zero before the player made a choice. Require explicit opt-in.
        if (tag.getInteger("settingsVersion") == 0 && parallelSetting.signum() == 0)
            parallelSetting = BigInteger.valueOf(Integer.MAX_VALUE);
        voltageSetting = tag.hasKey("voltageSetting") ? Math.max(1L, tag.getLong("voltageSetting")) : Integer.MAX_VALUE;
        targetDuration = tag.hasKey("targetDuration") ? Math.max(1, tag.getInteger("targetDuration")) : 128;
        parallels = BigValueCodec.readNBT(tag, "parallels", "parallelsBig")
            .toBigInteger()
            .max(BigInteger.ZERO);
        euPerTick = BigValueCodec.readNBT(tag, "eut", "eutBig")
            .toBigInteger()
            .max(BigInteger.ZERO);
        duration = tag.getInteger("duration");
        running = tag.getBoolean("running") && duration > 0 && parallels.signum() > 0;
        lossless = tag.getBoolean("lossless");
        exactDebit = tag.getBoolean("exactDebit") || lossless;
        totalEnergy = BigValueCodec.readNBT(tag, "totalEnergy", "totalEnergyBig")
            .toBigInteger()
            .max(BigInteger.ZERO);
        paidTicks = Math.max(0, Math.min(duration, tag.getInteger("paidTicks")));
        recipeOutputs.load(tag.getCompoundTag("recipe"));
        pendingOutputs.load(tag.getCompoundTag("pending"));
    }
}
