package com.silvia.apeiron.api.machine.parallel;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;

import appeng.api.storage.data.IAEStack;

/** Exact output factors and whole-cycle cost for one consumed input item. */
public final class ItemProcessingRecipe {

    private final BigInteger totalEU;
    private final List<IAEStack<?>> outputs;

    public ItemProcessingRecipe(BigInteger totalEU, BigMachineOutputQueue outputs) {
        this.totalEU = Objects.requireNonNull(totalEU, "totalEU");
        if (totalEU.signum() < 0) throw new IllegalArgumentException("Negative recipe cost");
        this.outputs = Objects.requireNonNull(outputs, "outputs")
            .snapshotOutputsUnsorted();
    }

    public BigInteger getTotalEU() {
        return totalEU;
    }

    /** Detached output factors keep planning and commit consistent even if a provider reuses a recipe. */
    public List<IAEStack<?>> getOutputs() {
        List<IAEStack<?>> copies = new ArrayList<>(outputs.size());
        for (IAEStack<?> output : outputs) copies.add(output.copy());
        return copies;
    }
}
