package com.silvia.apeiron.mixin.ae.crafting.gui;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.silvia.apeiron.ae.crafting.diagnostics.BigDiagnosticGuiRow;
import com.silvia.apeiron.ae.terminal.BigGuiNumberCapture;

import appeng.api.config.DiagnosticSortMode;
import appeng.client.gui.implementations.GuiCraftingDiagnosticTerminal;
import appeng.container.implementations.ContainerCraftingDiagnosticTerminal;

/** Uses exact row values for diagnostic sorting and tooltip output. */
@Mixin(value = GuiCraftingDiagnosticTerminal.class, remap = false)
public abstract class GuiCraftingDiagnosticTerminalBigMixin {

    @Shadow
    @Final
    private ContainerCraftingDiagnosticTerminal container;

    @Shadow
    @Final
    private List<?> rows;

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
            case AVG_PER_SECOND -> comparator = Comparator.comparingDouble(
                row -> ((BigDiagnosticGuiRow) row).getItemsPerSecondBig());
            case CUMULATIVE_TIME -> comparator = (left, right) -> elapsed(left).compareTo(elapsed(right));
            default -> comparator = (left, right) -> 0;
        }
        if (!this.container.ascending) comparator = comparator.reversed();
        values.sort(comparator);
    }

    @Redirect(
        method = "drawFG",
        at = @At(value = "FIELD", target = "Lappeng/client/gui/implementations/GuiCraftingDiagnosticTerminal$Row;totalProduced:J"))
    private long apeiron$captureTooltipTotal(final Object row) {
        if ((Object) row instanceof BigDiagnosticGuiRow exact) {
            return BigGuiNumberCapture.captureAmount(exact.getTotalProducedBig());
        }
        return 0L;
    }

    @Redirect(
        method = "drawFG",
        at = @At(value = "INVOKE", target = "Ljava/lang/StringBuilder;append(J)Ljava/lang/StringBuilder;"))
    private StringBuilder apeiron$formatTooltipLong(final StringBuilder builder, final long value) {
        return builder.append(BigGuiNumberCapture.formatExactAmount(value));
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
