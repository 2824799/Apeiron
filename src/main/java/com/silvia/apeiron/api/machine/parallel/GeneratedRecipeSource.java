package com.silvia.apeiron.api.machine.parallel;

import java.math.BigInteger;
import java.util.List;

import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;

import appeng.api.storage.data.IAEStack;
import gregtech.api.util.GTRecipe;

/** Fixed-cycle generators provide their native inputs and one output roll; execution is shared. */
public interface GeneratedRecipeSource {

    GTRecipe findRecipe(List<IAEStack<?>> inputs);

    /** Called once per batch, never once per parallel or during output-capacity binary search. */
    BigMachineOutputQueue rollOutputs(GTRecipe recipe);

    default BigInteger getFixedEUt(GTRecipe recipe) {
        return BigInteger.ZERO;
    }
}
