package com.silvia.apeiron.mixin.ae.terminal.pattern;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.terminal.BigAmountGui;

import appeng.api.storage.data.IAEStack;
import appeng.client.gui.implementations.GuiPatternValueAmount;

/** Sends the exact amount entered in the pattern-value dialog. */
@Mixin(value = GuiPatternValueAmount.class, remap = false)
public abstract class GuiPatternValueAmountBigMixin {

    @Redirect(
        method = { "actionPerformed", "func_146284_a" },
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IAEStack;setStackSize(J)Lappeng/api/storage/data/IAEStack;"))
    private IAEStack<?> apeiron$setExact(final IAEStack<?> stack, final long legacyAmount) {
        final BigInteger exact = ((BigAmountGui) (Object) this).getAmountBig();
        return exact == null ? stack.setStackSize(legacyAmount) : BigAEStackValues.set(stack, exact);
    }
}
