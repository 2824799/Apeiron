package com.silvia.apeiron.ae.terminal;

import java.util.Arrays;
import java.util.function.IntPredicate;

/** A display-only row projection. Every visible slot still addresses its original inventory index. */
public final class PatternRowLayout {

    private final int rowSize;
    private final int numSlots;
    private final int[] physicalRows;

    private PatternRowLayout(int rowSize, int numSlots, int[] physicalRows) {
        this.rowSize = rowSize;
        this.numSlots = numSlots;
        this.physicalRows = physicalRows;
    }

    public static PatternRowLayout freeRows(int rows, int rowSize, int numSlots, IntPredicate occupied) {
        if (rows < 0 || rowSize <= 0 || numSlots < 0) throw new IllegalArgumentException("Invalid pattern layout");
        int[] selected = new int[rows];
        int count = 0;
        int firstEmpty = -1;
        for (int row = 0; row < rows; row++) {
            int start = row * rowSize;
            int end = Math.min(start + rowSize, numSlots);
            boolean hasItem = false, hasSpace = false;
            for (int slot = start; slot < end; slot++) {
                if (occupied.test(slot)) hasItem = true;
                else hasSpace = true;
                if (hasItem && hasSpace) break;
            }
            if (hasItem && hasSpace) {
                selected[count++] = row;
            } else if (hasSpace && firstEmpty < 0) firstEmpty = row;
        }
        // Continue filling existing rows first. When none has room, expose only the first completely empty row.
        if (count == 0 && firstEmpty >= 0) selected[count++] = firstEmpty;
        return new PatternRowLayout(rowSize, numSlots, Arrays.copyOf(selected, count));
    }

    public int rowCount() {
        return physicalRows.length;
    }

    /** Return the real inventory's upper bound for an invalid display cell, so native click guards reject it. */
    public int physicalSlot(int displaySlot) {
        if (displaySlot < 0) return numSlots;
        int row = displaySlot / rowSize;
        if (row >= physicalRows.length) return numSlots;
        return Math.min(numSlots, physicalRows[row] * rowSize + displaySlot % rowSize);
    }
}
