package com.silvia.apeiron.mixin.ae.terminal.core;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.silvia.apeiron.ae.terminal.InterfaceTerminalRows;

/** Compact rows contribute their displayed height to AE's native section and scrollbar calculations. */
@Mixin(targets = "appeng.client.gui.implementations.GuiInterfaceTerminal$InterfaceSection", remap = false)
public abstract class InterfaceTerminalSectionRowsMixin {

    @Redirect(
        method = "getEntriesHeight",
        at = @At(
            value = "FIELD",
            target = "Lappeng/client/gui/implementations/GuiInterfaceTerminal$InterfaceTerminalEntry;guiHeight:I",
            opcode = Opcodes.GETFIELD))
    private int apeiron$entryHeight(@Coerce Object entry) {
        return ((InterfaceTerminalRows) entry).apeiron$displayHeight();
    }
}
