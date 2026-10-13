package com.silvia.apeiron.common.machine.parallel;

import java.math.BigInteger;
import java.util.List;

import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.api.machine.parallel.ParallelLimit;

import appeng.api.storage.data.IAEStack;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Independent native recipe steps can share one batch budget without starting the controller repeatedly. */
public final class ExactProcessingContext implements AutoCloseable {

    private static final ThreadLocal<ExactProcessingContext> ACTIVE = new ThreadLocal<>();
    private static final ThreadLocal<MTEMultiBlockBase> STARTING = new ThreadLocal<>();
    private final ExactProcessingContext previous;
    private final MTEMultiBlockBase machine;
    public final WirelessRecipeState state = new WirelessRecipeState();
    public final ParallelLimit limit;
    public final BigInteger reservedEnergy;
    public final List<IAEStack<?>> reservedOutputs;

    public ExactProcessingContext(MTEMultiBlockBase machine, ParallelLimit limit, BigInteger reservedEnergy,
        List<IAEStack<?>> reservedOutputs) {
        this.machine = machine;
        this.limit = limit;
        this.reservedEnergy = reservedEnergy;
        this.reservedOutputs = reservedOutputs;
        WirelessRecipeState settings = ((BigWirelessController) machine).getWirelessRecipeState();
        state.setParallelSettingBig(settings.getParallelSettingBig());
        state.setVoltageSetting(settings.getVoltageSetting());
        state.setTargetDuration(com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches.targetDuration(machine));
        previous = ACTIVE.get();
        ACTIVE.set(this);
    }

    public static ExactProcessingContext find(MTEMultiBlockBase machine) {
        ExactProcessingContext context = ACTIVE.get();
        return context != null && context.machine == machine ? context : null;
    }

    public static WirelessRecipeState state(MTEMultiBlockBase machine) {
        ExactProcessingContext context = find(machine);
        return context == null ? ((BigWirelessController) machine).getWirelessRecipeState() : context.state;
    }

    public static gregtech.api.recipe.check.CheckRecipeResult start(MTEMultiBlockBase machine,
        java.util.function.Supplier<gregtech.api.recipe.check.CheckRecipeResult> nativeHook) {
        MTEMultiBlockBase previous = STARTING.get();
        STARTING.set(machine);
        try {
            return nativeHook.get();
        } finally {
            if (previous == null) STARTING.remove();
            else STARTING.set(previous);
        }
    }

    public static boolean isStarting(MTEMultiBlockBase machine) {
        return STARTING.get() == machine;
    }

    @Override
    public void close() {
        if (previous == null) ACTIVE.remove();
        else ACTIVE.set(previous);
    }
}
