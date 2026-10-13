package com.silvia.apeiron.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

import appeng.api.config.Settings;
import appeng.client.gui.widgets.GuiImgButton;

/** The original button and hit box, with a third state and a compact row icon. */
public final class InterfaceTerminalFilterButton extends GuiImgButton {

    public InterfaceTerminalFilterButton(int x, int y) {
        super(x, y, Settings.ACTIONS, null);
    }

    @Override
    public String getMessage() {
        return StatCollector.translateToLocal("apeiron.gui.interface_terminal.filter.title") + "\n"
            + StatCollector.translateToLocal(
                InterfaceTerminalFilterMode.current()
                    .translationKey())
            + "\n"
            + StatCollector.translateToLocal("apeiron.gui.interface_terminal.filter.cycle");
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (InterfaceTerminalFilterMode.current() != InterfaceTerminalFilterMode.FREE_ROWS) {
            super.drawButton(mc, mouseX, mouseY);
            return;
        }
        Enum<?> nativeValue = getCurrentValue();
        set(null);
        try {
            super.drawButton(mc, mouseX, mouseY);
        } finally {
            set(nativeValue);
        }
        if (!visible) return;
        int color = enabled ? 0xFF80FF80 : 0xFF608060;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            int x = xPosition + 3 + col * 4, y = yPosition + 3 + row * 4;
            drawRect(x, y, x + 3, y + 3, row == 0 ? 0xFF707070 : color);
        }
    }
}
