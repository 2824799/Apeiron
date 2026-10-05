package com.silvia.apeiron.common.machine.tectech;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.common.machine.me.stocking.StockingInputHost;
import com.silvia.apeiron.common.machine.me.stocking.StockingInputLogic;
import com.silvia.apeiron.common.machine.parallel.BigRecipeInventory;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** EOH fills its internal tanks outside the normal recipe bracket; charge ME before filling those tanks. */
public final class EyeOfHarmonyInputSupport {

    private EyeOfHarmonyInputSupport() {}

    public static void drain(MTEMultiBlockBase machine, Map<Fluid, Long> stored) {
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
            List<IAEStack<?>> stocks = inventory.getStacksBig();
            BigInteger[] debit = new BigInteger[stocks.size()];
            Map<Fluid, Long> next = new LinkedHashMap<>(stored);
            for (int i = 0; i < stocks.size(); i++) {
                IAEStack<?> stock = stocks.get(i);
                Fluid type = ((IAEFluidStack) stock).getFluidStack()
                    .getFluid();
                debit[i] = BigInteger.ZERO;
                if (!next.containsKey(type)) continue;
                BigInteger room = BigInteger.valueOf(Long.MAX_VALUE)
                    .subtract(BigInteger.valueOf(next.get(type)));
                BigInteger amount = BigAEStackValues.get(stock)
                    .min(BigInteger.valueOf(Integer.MAX_VALUE))
                    .min(room);
                if (amount.signum() <= 0) continue;
                debit[i] = amount;
                next.put(type, next.get(type) + amount.longValueExact());
            }
            try {
                inventory.consume(debit);
            } catch (IllegalStateException unavailable) {
                // A concurrent consumer can invalidate a cached network view. Retry without crediting EOH.
                return;
            }
            stored.clear();
            stored.putAll(next);
            machine.updateSlots();
        } finally {
            StockingInputLogic.finishGroup(started);
        }
    }
}
