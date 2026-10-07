package com.silvia.apeiron.common.machine.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.IDualInputInventory;

/** Custom recipe readers must try buffers separately, never merge unrelated pattern inventories. */
public final class IsolatedRecipeInputs {

    private IsolatedRecipeInputs() {}

    public static final class Candidate {

        public final List<ItemStack> items = new ArrayList<>();
        public final List<FluidStack> fluids = new ArrayList<>();
    }

    public static List<Candidate> candidates(Iterable<? extends IDualInputHatch> hatches) {
        List<Candidate> result = new ArrayList<>();
        Set<IDualInputInventory> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (IDualInputHatch hatch : hatches) {
            for (java.util.Iterator<? extends IDualInputInventory> it = hatch.inventories(); it.hasNext();) {
                IDualInputInventory inventory = it.next();
                if (!seen.add(inventory) || inventory.isEmpty()) continue;
                Candidate candidate = new Candidate();
                Collections.addAll(candidate.items, hatch.getSharedItems());
                Collections.addAll(candidate.items, inventory.getItemInputs());
                Collections.addAll(candidate.fluids, inventory.getFluidInputs());
                candidate.items.removeIf(java.util.Objects::isNull);
                candidate.fluids.removeIf(java.util.Objects::isNull);
                result.add(candidate);
            }
        }
        return result;
    }

    public static List<List<ItemStack>> patterns(Iterable<? extends IDualInputHatch> hatches) {
        List<List<ItemStack>> result = new ArrayList<>();
        for (Candidate candidate : candidates(hatches)) result.add(candidate.items);
        return result;
    }

    public static List<List<ItemStack>> specialBusses(List<MTEHatchInputBus> busses) {
        List<IDualInputHatch> patterns = new ArrayList<>();
        List<ItemStack> ordinary = new ArrayList<>();
        for (MTEHatchInputBus bus : busses) {
            if (!bus.isValid()) continue;
            if (bus instanceof IDualInputHatch) {
                patterns.add((IDualInputHatch) bus);
                continue;
            }
            for (int slot = 0; slot < bus.getSizeInventory(); slot++) {
                ItemStack stack = bus.getStackInSlot(slot);
                if (stack != null) ordinary.add(stack);
            }
        }
        List<List<ItemStack>> result = patterns(patterns);
        result.add(ordinary);
        return result;
    }
}
