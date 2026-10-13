package com.silvia.apeiron.mixin.ae.terminal.core;

import net.minecraft.item.ItemStack;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.terminal.InterfaceTerminalRows;
import com.silvia.apeiron.ae.terminal.PatternRowLayout;
import com.silvia.apeiron.client.gui.InterfaceTerminalFilterMode;

import appeng.tile.inventory.AppEngInternalInventory;

/** The physical inventory never changes; cache only a list of original row numbers with free slots. */
@Mixin(targets = "appeng.client.gui.implementations.GuiInterfaceTerminal$InterfaceTerminalEntry", remap = false)
public abstract class InterfaceTerminalEntryRowsMixin implements InterfaceTerminalRows {

    @Shadow
    private AppEngInternalInventory inv;
    @Shadow
    private int rows;
    @Shadow
    private int rowSize;
    @Shadow
    private int numSlots;
    @Shadow
    private int guiHeight;

    @Unique
    private PatternRowLayout apeiron$layout;
    @Unique
    private int apeiron$rows, apeiron$rowSize, apeiron$numSlots;

    @Unique
    private PatternRowLayout apeiron$freeRows() {
        if (apeiron$layout == null || apeiron$rows != rows
            || apeiron$rowSize != rowSize
            || apeiron$numSlots != numSlots) {
            apeiron$layout = PatternRowLayout.freeRows(
                rows,
                rowSize,
                numSlots,
                slot -> slot < inv.getSizeInventory() && inv.getStackInSlot(slot) != null);
            apeiron$rows = rows;
            apeiron$rowSize = rowSize;
            apeiron$numSlots = numSlots;
        }
        return apeiron$layout;
    }

    @Inject(method = "setItemInSlot", at = @At("RETURN"))
    private void apeiron$invalidateRows(ItemStack stack, int slot, CallbackInfo ci) {
        apeiron$layout = null;
    }

    @Override
    public int apeiron$visibleRowCount() {
        return InterfaceTerminalFilterMode.current() == InterfaceTerminalFilterMode.FREE_ROWS
            ? apeiron$freeRows().rowCount()
            : rows;
    }

    @Override
    public int apeiron$displayHeight() {
        return InterfaceTerminalFilterMode.current() == InterfaceTerminalFilterMode.FREE_ROWS
            ? apeiron$visibleRowCount() * 18 + guiHeight - rows * 18
            : guiHeight;
    }

    @Override
    public int apeiron$physicalSlot(int displaySlot) {
        return InterfaceTerminalFilterMode.current() == InterfaceTerminalFilterMode.FREE_ROWS
            ? apeiron$freeRows().physicalSlot(displaySlot)
            : displaySlot;
    }

    @Redirect(
        method = "mouseClicked",
        at = @At(
            value = "FIELD",
            target = "Lappeng/client/gui/implementations/GuiInterfaceTerminal$InterfaceTerminalEntry;guiHeight:I",
            opcode = Opcodes.GETFIELD))
    private int apeiron$clickHeight(@Coerce Object entry) {
        return ((InterfaceTerminalRows) entry).apeiron$displayHeight();
    }

    @ModifyVariable(method = "mouseClicked", at = @At("STORE"), name = "slotIdx")
    private int apeiron$clickPhysicalSlot(int displaySlot) {
        return apeiron$physicalSlot(displaySlot);
    }
}
