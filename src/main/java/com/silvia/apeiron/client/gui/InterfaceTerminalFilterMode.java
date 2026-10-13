package com.silvia.apeiron.client.gui;

import appeng.core.AEConfig;

/** Extend AE's existing client preference without changing its setting enum or any server packets. */
public enum InterfaceTerminalFilterMode {

    ALL("all"),
    FREE_INTERFACES("interfaces"),
    FREE_ROWS("rows");

    private static boolean onlyFreeRows;
    private final String key;

    InterfaceTerminalFilterMode(String key) {
        this.key = key;
    }

    public String translationKey() {
        return "apeiron.gui.interface_terminal.filter." + key;
    }

    public static InterfaceTerminalFilterMode current() {
        if (!AEConfig.instance.showOnlyInterfacesWithFreeSlotsInInterfaceTerminal) return ALL;
        return onlyFreeRows ? FREE_ROWS : FREE_INTERFACES;
    }

    public void select() {
        onlyFreeRows = this == FREE_ROWS;
        AEConfig.instance.showOnlyInterfacesWithFreeSlotsInInterfaceTerminal = this != ALL;
    }

    public static void cycle(boolean backwards) {
        InterfaceTerminalFilterMode[] modes = values();
        modes[(current().ordinal() + (backwards ? modes.length - 1 : 1)) % modes.length].select();
    }
}
