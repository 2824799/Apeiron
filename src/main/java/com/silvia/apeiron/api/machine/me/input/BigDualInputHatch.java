package com.silvia.apeiron.api.machine.me.input;

import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;

import gregtech.common.tileentities.machines.IDualInputHatch;

/** Resolves mirrors to the one authoritative inventory for recipe processing. */
public interface BigDualInputHatch extends IDualInputHatch {

    MTEInfinitePatternInputAssembly getInputSource();
}
