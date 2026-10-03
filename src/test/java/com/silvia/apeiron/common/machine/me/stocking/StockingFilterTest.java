package com.silvia.apeiron.common.machine.me.stocking;

import static org.junit.Assert.*;

import org.junit.Test;

public class StockingFilterTest {

    @Test
    public void oreAndItemWildcardsAreLiteralExceptForGlobOperators() {
        assertTrue(StockingFilter.glob("dust*", "dustIron"));
        assertFalse(StockingFilter.glob("dust*", "ingotIron"));
        assertTrue(StockingFilter.glob("minecraft:*", "minecraft:diamond"));
        assertTrue(StockingFilter.glob("tile.foo[1]", "tile.foo[1]"));
        assertFalse(StockingFilter.glob("tile.foo[1]", "tile.foof"));
        assertTrue(StockingFilter.glob("", "any"));
        assertTrue(StockingFilter.glob("circuit?", "circuit1"));
    }
}
