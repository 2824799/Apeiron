package com.silvia.apeiron.mixin.ae.terminal.core;

import net.minecraft.inventory.Container;

import org.lwjgl.input.Mouse;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.terminal.InterfaceTerminalRows;
import com.silvia.apeiron.client.gui.InterfaceTerminalFilterButton;
import com.silvia.apeiron.client.gui.InterfaceTerminalFilterMode;

import appeng.client.gui.implementations.GuiInterfaceTerminal;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.core.AEConfig;

/** Hide full rows while retaining AE's grouping, native renderer, search, tooltips and real slot indices. */
@Mixin(value = GuiInterfaceTerminal.class, remap = false)
public abstract class GuiInterfaceTerminalRowsMixin {

    @Shadow
    @Final
    @Mutable
    private GuiImgButton guiButtonHideFull;

    @Unique
    private InterfaceTerminalRows apeiron$drawnEntry;

    @Inject(method = "<init>(Lnet/minecraft/inventory/Container;)V", at = @At("RETURN"))
    private void apeiron$filterButton(Container container, CallbackInfo ci) {
        guiButtonHideFull = new InterfaceTerminalFilterButton(guiButtonHideFull.xPosition, guiButtonHideFull.yPosition);
    }

    @Redirect(
        method = { "actionPerformed", "func_146284_a" },
        at = @At(
            value = "FIELD",
            target = "Lappeng/core/AEConfig;showOnlyInterfacesWithFreeSlotsInInterfaceTerminal:Z",
            opcode = Opcodes.PUTFIELD))
    private void apeiron$cycleFilter(AEConfig config, boolean nativeToggle) {
        InterfaceTerminalFilterMode.cycle(Mouse.isButtonDown(1));
        // The original button handler still marks the list dirty and updates the scrollbar.
    }

    @Redirect(
        method = { "drawSection", "drawEntry" },
        at = @At(
            value = "FIELD",
            target = "Lappeng/client/gui/implementations/GuiInterfaceTerminal$InterfaceTerminalEntry;rows:I",
            opcode = Opcodes.GETFIELD))
    private int apeiron$visibleRows(@Coerce Object entry) {
        return ((InterfaceTerminalRows) entry).apeiron$visibleRowCount();
    }

    @Inject(method = "drawEntry", at = @At("HEAD"))
    private void apeiron$beginEntry(@Coerce Object entry, int viewY, int titleBottom, int mouseX, int mouseY,
        CallbackInfoReturnable<Integer> cir) {
        apeiron$drawnEntry = (InterfaceTerminalRows) entry;
    }

    @ModifyVariable(method = "drawEntry", at = @At("STORE"), name = "slotIdx", require = 2)
    private int apeiron$renderPhysicalSlot(int displaySlot) {
        return apeiron$drawnEntry.apeiron$physicalSlot(displaySlot);
    }
}
