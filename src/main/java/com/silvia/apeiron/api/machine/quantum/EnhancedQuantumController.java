package com.silvia.apeiron.api.machine.quantum;

/** Capabilities enabled only by a module registered during the native QFT structure scan. */
public interface EnhancedQuantumController {

    boolean hasQuantumEnhancement();

    int getQuantumCraftingTier();

    boolean isQuantumFluidMode();

    void prepareEnhancedQuantumRecipe(int parallels);
}
