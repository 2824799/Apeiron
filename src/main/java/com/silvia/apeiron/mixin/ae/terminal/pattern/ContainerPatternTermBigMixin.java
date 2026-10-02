package com.silvia.apeiron.mixin.ae.terminal.pattern;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.api.storage.data.IAEStack;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.tile.inventory.IAEStackInventory;

/** Applies pattern-terminal quantity changes through the exact AE stack sidecar. */
@Mixin(value = ContainerPatternTerm.class, remap = false)
public abstract class ContainerPatternTermBigMixin {

    @Overwrite
    static boolean canMultiplyOrDivide(final IAEStackInventory inventory, int multiplier) {
        if (multiplier > 0) return true;
        if (multiplier < 0) {
            multiplier = -multiplier;
            final BigInteger divisor = BigInteger.valueOf(multiplier);
            for (int i = 0; i < inventory.getSizeInventory(); i++) {
                final IAEStack<?> stack = inventory.getAEStackInSlot(i);
                if (stack != null && !BigAEStackValues.get(stack)
                    .mod(divisor)
                    .equals(BigInteger.ZERO)) return false;
            }
            return true;
        }
        return false;
    }

    @Overwrite
    static void multiplyOrDivideStacksInternal(final IAEStackInventory inventory, int multiplier) {
        if (multiplier == 0) return;
        final boolean dividing = multiplier < 0;
        if (dividing) multiplier = -multiplier;
        final BigInteger factor = BigInteger.valueOf(multiplier);
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            final IAEStack<?> stack = inventory.getAEStackInSlot(i);
            if (stack == null) continue;
            final BigInteger value = BigAEStackValues.get(stack);
            BigAEStackValues.set(stack, dividing ? value.divide(factor) : value.multiply(factor));
            inventory.putAEStackInSlot(i, stack);
        }
    }
}
