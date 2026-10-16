package com.silvia.apeiron.common.machine.me.circuit;

import net.minecraft.item.ItemStack;

import gregtech.api.util.GTUtility;
import gregtech.common.items.ItemIntegratedCircuit;

/** Native GT ghost circuits are selectors, never real inventory or consumable ME stock. */
public final class GlobalInputCircuit {

    private GlobalInputCircuit() {}

    public static ItemStack fromConfig(int config) {
        if (config < 1 || config > ItemIntegratedCircuit.MAX_CIRCUIT_NUMBER) return null;
        ItemStack circuit = GTUtility.getIntegratedCircuit(config);
        circuit.stackSize = 0;
        return circuit;
    }

    public static ItemStack normalize(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemIntegratedCircuit ? fromConfig(stack.getItemDamage())
            : null;
    }

    public static int config(ItemStack stack) {
        return stack == null ? 0 : stack.getItemDamage();
    }
}
