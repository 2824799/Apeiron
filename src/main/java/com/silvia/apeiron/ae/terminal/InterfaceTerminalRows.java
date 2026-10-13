package com.silvia.apeiron.ae.terminal;

/** Rendering, scroll height and click packets share the same projection of an AE interface entry. */
public interface InterfaceTerminalRows {

    int apeiron$visibleRowCount();

    int apeiron$displayHeight();

    int apeiron$physicalSlot(int displaySlot);
}
