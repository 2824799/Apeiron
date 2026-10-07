package com.silvia.apeiron.mixin.ae.crafting.gui;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.silvia.apeiron.ae.crafting.diagnostics.BigDiagnosticGuiRow;
import com.silvia.apeiron.math.BigNumberFormatter;

import appeng.api.config.DiagnosticSortMode;
import appeng.client.gui.implementations.GuiCraftingDiagnosticTerminal;
import appeng.container.implementations.ContainerCraftingDiagnosticTerminal;
import appeng.core.localization.GuiText;

/** Uses exact row values for diagnostic sorting and tooltip output. */
@Mixin(value = GuiCraftingDiagnosticTerminal.class, remap = false)
public abstract class GuiCraftingDiagnosticTerminalBigMixin {

    @Shadow
    @Final
    private ContainerCraftingDiagnosticTerminal container;

    @Shadow
    @Final
    private List<?> rows;

    @Shadow
    private int hoveredRow;

    @Overwrite
    private void applyClientSort() {
        @SuppressWarnings("unchecked")
        final List<Object> values = (List<Object>) (List<?>) this.rows;
        final DiagnosticSortMode mode = DiagnosticSortMode.values()[this.container.sortMode];
        Comparator<Object> comparator;
        switch (mode) {
            case NAME -> comparator = Comparator.comparing(
                row -> ((BigDiagnosticGuiRow) row).getDisplayNameForApeiron(),
                String.CASE_INSENSITIVE_ORDER);
            case CRAFTED -> comparator = (left, right) -> exact(left).compareTo(exact(right));
            case SAMPLES -> comparator = (left, right) -> samples(left).compareTo(samples(right));
            case AVG_PER_SECOND -> comparator = Comparator
                .comparingDouble(row -> ((BigDiagnosticGuiRow) row).getItemsPerSecondBig());
            case CUMULATIVE_TIME -> comparator = (left, right) -> elapsed(left).compareTo(elapsed(right));
            default -> comparator = (left, right) -> 0;
        }
        if (!this.container.ascending) comparator = comparator.reversed();
        values.sort(comparator);
    }

    // Modify the finished tooltip rather than private Row fields: older compilers use synthetic accessors.
    @ModifyArg(
        method = "drawFG",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/client/gui/implementations/GuiCraftingDiagnosticTerminal;drawTooltip(II[Ljava/lang/String;)V"),
        index = 2)
    private String[] apeiron$exactTooltip(String[] lines) {
        if (hoveredRow < 0 || hoveredRow >= rows.size()) return lines;
        BigDiagnosticGuiRow row = (BigDiagnosticGuiRow) rows.get(hoveredRow);
        apeiron$replaceCount(lines, GuiText.Crafted.getLocal() + ": ", row.getTotalProducedBig());
        apeiron$replaceCount(lines, GuiText.Samples.getLocal() + ": ", row.getSampleCountBig());
        return lines;
    }

    @org.spongepowered.asm.mixin.Unique
    private static void apeiron$replaceCount(String[] lines, String prefix, BigInteger value) {
        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].startsWith(prefix)) {
                lines[i] = prefix + BigNumberFormatter.formatExact(value);
                return;
            }
        }
    }

    private static BigInteger exact(final Object row) {
        return ((BigDiagnosticGuiRow) row).getTotalProducedBig();
    }

    private static BigInteger samples(final Object row) {
        return ((BigDiagnosticGuiRow) row).getSampleCountBig();
    }

    private static BigInteger elapsed(final Object row) {
        return ((BigDiagnosticGuiRow) row).getElapsedTimeTicksBig();
    }
}
