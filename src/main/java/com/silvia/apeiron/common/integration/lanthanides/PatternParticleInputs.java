package com.silvia.apeiron.common.integration.lanthanides;

import com.silvia.apeiron.api.machine.me.input.BigDualInputHatch;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;

import gregtech.api.metatileentity.MetaTileEntity;

/** Enables particle items only for machines connected to a live Apeiron pattern input. */
public final class PatternParticleInputs {

    private PatternParticleInputs() {}

    public static boolean isPatternInput(Object hatch) {
        if (!(hatch instanceof BigDualInputHatch) || !(hatch instanceof MetaTileEntity)
            || !((MetaTileEntity) hatch).isValid()) return false;
        MTEInfinitePatternInputAssembly source = ((BigDualInputHatch) hatch).getInputSource();
        return source != null && source.isValid();
    }

    public static boolean hasPatternInput(Iterable<?> hatches) {
        for (Object hatch : hatches) if (isPatternInput(hatch)) return true;
        return false;
    }
}
