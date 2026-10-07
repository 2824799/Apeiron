package com.silvia.apeiron.mixin.ae.stack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.silvia.apeiron.ae.terminal.BigCellTooltipNumbers;

import appeng.api.storage.data.IAEStack;
import appeng.items.AEBaseCell;
import appeng.util.ReadableNumberConverter;

/** Exact contents tooltip, selected only on releases providing this native display. */
@Mixin(value = AEBaseCell.class, remap = false)
public abstract class AEBaseCellContentsMixin {

    @Redirect(
        method = "addCheckedInformation",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IAEStack;getStackSize()J"))
    private long apeiron$captureStackAmount(final IAEStack<?> stack) {
        return BigCellTooltipNumbers.captureStack(stack);
    }

    @Redirect(
        method = "addCheckedInformation",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/util/ReadableNumberConverter;toWideReadableForm(J)Ljava/lang/String;"))
    private String apeiron$formatStackAmount(final ReadableNumberConverter converter, final long value) {
        return BigCellTooltipNumbers.formatStack(value);
    }
}
