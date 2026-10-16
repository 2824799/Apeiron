package com.silvia.apeiron.api.machine.output;

import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;

/** Exact products alongside the native parallel, input and power calculation. */
public interface NativeRecipeOutputHelper {

    void enableExactNativeOutputs(boolean items, boolean fluids);

    void setNativeFluidOutputProvider(com.silvia.apeiron.api.machine.parallel.BigRecipeOutputProvider provider);

    BigMachineOutputQueue getNativeRecipeOutputsBig();
}
