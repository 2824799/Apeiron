package com.silvia.apeiron.common.machine.tectech;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.common.machine.me.stocking.StockingInputHost;
import com.silvia.apeiron.common.machine.me.stocking.StockingInputLogic;
import com.silvia.apeiron.common.machine.parallel.BigRecipeInventory;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** EOH fills its internal tanks outside the normal recipe bracket; charge ME before filling those tanks. */
public final class EyeOfHarmonyInputSupport {

    private EyeOfHarmonyInputSupport() {}

    public static void drain(MTEMultiBlockBase machine, Map<Fluid, Long> stored, long astralArrays, boolean enhanced) {
        Map<Fluid, BigInteger> targets = EyeOfHarmonyFluidRequirements.selected(machine, astralArrays, enhanced);
        if (targets.isEmpty() || targets.entrySet()
            .stream()
            .noneMatch(
                entry -> entry.getValue()
                    .compareTo(BigInteger.valueOf(stored.getOrDefault(entry.getKey(), 0L))) > 0))
            return;

        Set<StockingInputLogic> started = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Object> hatches = new ArrayList<>();
        hatches.addAll(machine.mInputBusses);
        hatches.addAll(machine.mInputHatches);
        hatches.addAll(machine.mDualInputHatches);
        for (Object hatch : hatches) if (hatch instanceof StockingInputHost) {
            StockingInputLogic logic = ((StockingInputHost) hatch).getStockingInput();
            if (!logic.isProcessing() && started.add(logic)) logic.begin();
        }
        try {
            BigRecipeInventory inventory = new BigRecipeInventory(
                machine,
                new ItemStack[0],
                machine.getStoredFluids()
                    .toArray(new FluidStack[0]));
            if (inventory.fillFluids(stored, targets)) machine.updateSlots();
        } catch (IllegalStateException unavailable) {
            // The cached network view may have changed. The exact transaction rolls back before tank credit.
        } finally {
            StockingInputLogic.finishGroup(started);
        }
    }
}
