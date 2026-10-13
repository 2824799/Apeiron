package com.silvia.apeiron.ae.terminal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PatternRowLayoutTest {

    @Test
    public void jumpsStraightToTheLastFreeRowOfA360SlotAssembly() {
        PatternRowLayout layout = PatternRowLayout.freeRows(40, 9, 360, slot -> slot != 359);
        assertEquals(1, layout.rowCount());
        assertEquals(351, layout.physicalSlot(0));
        assertEquals(359, layout.physicalSlot(8));
        assertEquals(360, layout.physicalSlot(9));
    }

    @Test
    public void preservesOrderAndOccupiedNeighboursAcrossNonAdjacentFreeRows() {
        PatternRowLayout layout = PatternRowLayout.freeRows(40, 9, 360, slot -> slot != 10 && slot != 350);
        assertEquals(2, layout.rowCount());
        assertEquals(9, layout.physicalSlot(0));
        assertEquals(17, layout.physicalSlot(8));
        assertEquals(342, layout.physicalSlot(9));
        assertEquals(350, layout.physicalSlot(17));
    }

    @Test
    public void supportsDifferentInterfaceWidths() {
        PatternRowLayout layout = PatternRowLayout.freeRows(36, 10, 360, slot -> slot != 105);
        assertEquals(1, layout.rowCount());
        assertEquals(100, layout.physicalSlot(0));
        assertEquals(109, layout.physicalSlot(9));
    }

    @Test
    public void doesNotTreatNonexistentCellsInAPartialRowAsFreeSlots() {
        PatternRowLayout full = PatternRowLayout.freeRows(2, 9, 13, slot -> true);
        assertEquals(0, full.rowCount());
        PatternRowLayout partial = PatternRowLayout.freeRows(2, 9, 13, slot -> slot != 12);
        assertEquals(1, partial.rowCount());
        assertEquals(12, partial.physicalSlot(3));
        assertEquals(13, partial.physicalSlot(4));
    }

    @Test
    public void exposesOnlyTheFirstRowOfAnEmptyInterface() {
        PatternRowLayout layout = PatternRowLayout.freeRows(40, 9, 360, slot -> false);
        assertEquals(1, layout.rowCount());
        assertEquals(8, layout.physicalSlot(8));
        assertEquals(360, layout.physicalSlot(9));
        assertEquals(360, layout.physicalSlot(-1));
    }

    @Test
    public void exposesTheNextEmptyRowOnlyAfterTheCurrentRowIsFull() {
        boolean[] occupied = new boolean[360];
        for (int slot = 0; slot < 9; slot++) {
            occupied[slot] = true;
            PatternRowLayout layout = PatternRowLayout.freeRows(40, 9, 360, index -> occupied[index]);
            assertEquals(1, layout.rowCount());
            assertEquals(slot == 8 ? 9 : 0, layout.physicalSlot(0));
        }
    }

    @Test
    public void showsAllPartiallyFilledRowsWithoutAddingEmptyRows() {
        PatternRowLayout layout = PatternRowLayout.freeRows(40, 9, 360, slot -> slot == 10 || slot == 350);
        assertEquals(2, layout.rowCount());
        assertEquals(9, layout.physicalSlot(0));
        assertEquals(342, layout.physicalSlot(9));
    }

    @Test
    public void hasNoVisibleRowsOrClickableSlotsWhenFull() {
        PatternRowLayout layout = PatternRowLayout.freeRows(40, 9, 360, slot -> true);
        assertEquals(0, layout.rowCount());
        assertEquals(360, layout.physicalSlot(0));
    }
}
