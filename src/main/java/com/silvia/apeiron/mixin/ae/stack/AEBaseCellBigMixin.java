package com.silvia.apeiron.mixin.ae.stack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.silvia.apeiron.ae.storage.BigCellInventory;
import com.silvia.apeiron.ae.terminal.BigCellTooltipNumbers;

import appeng.items.AEBaseCell;
import appeng.me.storage.CellInventory;

/** Keeps storage-cell hover text exact for used bytes and contained item amounts. */
@Mixin(value = AEBaseCell.class, remap = false)
public abstract class AEBaseCellBigMixin {

    @Redirect(
        method = "addCheckedInformation",
        at = @At(value = "INVOKE", target = "Lappeng/me/storage/CellInventory;getUsedBytes()J"))
    private long apeiron$captureUsedBytes(final CellInventory inventory) {
        if (inventory instanceof BigCellInventory exact) {
            return BigCellTooltipNumbers.captureNumber(exact.getUsedBytesBig());
        }
        return inventory.getUsedBytes();
    }

    @Redirect(
        method = "addCheckedInformation",
        at = @At(value = "INVOKE", target = "Lappeng/me/storage/CellInventory;getTotalBytes()J"))
    private long apeiron$captureTotalBytes(final CellInventory inventory) {
        if (inventory instanceof BigCellInventory exact) {
            return BigCellTooltipNumbers.captureNumber(exact.getTotalBytesBig());
        }
        return inventory.getTotalBytes();
    }

    @Redirect(
        method = "addCheckedInformation",
        at = @At(value = "INVOKE", target = "Ljava/text/NumberFormat;format(J)Ljava/lang/String;"))
    private String apeiron$formatCellNumber(final java.text.NumberFormat formatter, final long value) {
        return BigCellTooltipNumbers.formatNumber(formatter, value);
    }

}
