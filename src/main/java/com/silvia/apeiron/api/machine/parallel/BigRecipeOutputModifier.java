package com.silvia.apeiron.api.machine.parallel;

import java.util.List;
import java.util.function.UnaryOperator;

import appeng.api.storage.data.IAEStack;
import gregtech.api.util.GTRecipe;

/** Snapshots a native post-parallel output rule before its processing logic clears temporary recipe fields. */
public interface BigRecipeOutputModifier {

    UnaryOperator<List<IAEStack<?>>> snapshotOutputModifier(GTRecipe recipe);
}
