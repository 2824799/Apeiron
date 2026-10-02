package com.silvia.apeiron.ae.crafting.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.inventory.InventoryCrafting;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.storage.data.IAEStack;
import appeng.crafting.MECraftingInventory;
import appeng.util.Platform;
import appeng.util.inv.MEInventoryCrafting;

/** Batches a processing pattern by distinct ingredient types, with rollback before ownership changes. */
public final class PatternBatchDispatch {

    private PatternBatchDispatch() {}

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static BigInteger dispatch(MTEInfinitePatternInputAssembly machine, ICraftingPatternDetails pattern,
        InventoryCrafting table, MECraftingInventory inventory, BigInteger limit, IEnergyGrid energy) {
        IAEStack<?>[] declared = pattern.getAEInputs();
        List<IAEStack<?>> inputs = new ArrayList<>();
        for (int slot = 0; slot < table.getSizeInventory(); slot++) {
            IAEStack<?> input = table instanceof MEInventoryCrafting
                ? ((MEInventoryCrafting) table).getAEStackInSlot(slot)
                : null;
            if (input == null) input = Platform.convertStackPacket(table.getStackInSlot(slot));
            if (declared.length == table.getSizeInventory() && !BigAEStackValues.get(input)
                .equals(BigAEStackValues.get(declared[slot]))) return BigInteger.ZERO;
            if (input != null && BigAEStackValues.get(input)
                .signum() > 0) {
                boolean merged = false;
                for (IAEStack existing : inputs)
                    if (existing.getStackType() == input.getStackType() && existing.isSameType(input)) {
                        BigAEStackValues.set(
                            existing,
                            BigAEStackValues.get(existing)
                                .add(BigAEStackValues.get(input)));
                        merged = true;
                        break;
                    }
                if (!merged) inputs.add(input.copy());
            }
        }
        // Native long comparisons can mistake two saturated values for equal quantities.
        // Only dispatch a complete exact table; otherwise the CPU returns the first extraction.
        if (!pattern.canSubstitute()) for (IAEStack<?> required : pattern.getCondensedAEInputs()) {
            if (required == null || BigAEStackValues.get(required)
                .signum() <= 0) continue;
            IAEStack<?> supplied = null;
            for (IAEStack candidate : inputs)
                if (candidate.getStackType() == required.getStackType() && candidate.isSameType(required)) {
                    supplied = candidate;
                    break;
                }
            if (!BigAEStackValues.get(required)
                .equals(BigAEStackValues.get(supplied))) return BigInteger.ZERO;
        }
        BigInteger batches = limit.max(BigInteger.ONE);
        BigDecimal cost = BigDecimal.ONE;
        for (IAEStack input : inputs) {
            BigInteger perBatch = BigAEStackValues.get(input);
            IAEStack available = inventory.extractItems(
                BigAEStackValues.copyWithSize(input, perBatch.multiply(batches.subtract(BigInteger.ONE))),
                Actionable.SIMULATE);
            batches = batches.min(
                BigAEStackValues.get(available)
                    .divide(perBatch)
                    .add(BigInteger.ONE));
            cost = cost.add(new BigDecimal(perBatch).divide(BigDecimal.valueOf(input.getAmountPerUnit())));
        }
        // The native CPU already reserved the first operation's power. Bound only extra operations here.
        double balance = energy.extractAEPower(Double.MAX_VALUE, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (Double.isFinite(balance)) {
            BigInteger powered = BigDecimal.valueOf(Math.max(0, balance))
                .divideToIntegralValue(cost)
                .toBigInteger();
            batches = batches.min(powered.max(BigInteger.ONE));
        }
        if (!machine.pushPatternBig(pattern, inputs, batches, true)) return BigInteger.ZERO;
        BigInteger extra = batches.subtract(BigInteger.ONE);
        List<IAEStack<?>> extracted = new ArrayList<>();
        boolean committed = false;
        try {
            if (extra.signum() > 0) for (IAEStack input : inputs) {
                BigInteger count = BigAEStackValues.get(input)
                    .multiply(extra);
                IAEStack stack = inventory
                    .extractItems(BigAEStackValues.copyWithSize(input, count), Actionable.MODULATE);
                if (stack != null) extracted.add(stack);
                if (!BigAEStackValues.get(stack)
                    .equals(count)) return BigInteger.ZERO;
            }
            if (!machine.pushPatternBig(pattern, inputs, batches, false)) return BigInteger.ZERO;
            committed = true;
            if (extra.signum() > 0) energy.extractAEPower(
                cost.multiply(new BigDecimal(extra))
                    .doubleValue(),
                Actionable.MODULATE,
                PowerMultiplier.CONFIG);
            return batches;
        } finally {
            if (!committed) for (IAEStack stack : extracted) inventory.injectItems(stack, Actionable.MODULATE);
        }
    }
}
