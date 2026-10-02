// SPDX-License-Identifier: LGPL-3.0-only
// Ported from GT5-Unofficial 5.09.54.190 (GT New Horizons contributors); modified by Apeiron.
package com.silvia.apeiron.client.gui.machine.me.output;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputBus;
import com.silvia.apeiron.common.machine.me.output.storage.BigMEOutputProvider;

import appeng.core.localization.GuiText;
import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;

public class BoundlessMEOutputBusGui extends MTEHatchBaseGui<MTEBoundlessMEOutputBus> {

    public BoundlessMEOutputBusGui(MTEBoundlessMEOutputBus hatch) {
        super(hatch);
    }

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager syncManager) {
        return super.createContentSection(panel, syncManager).child(createSettingsRow(machine.getProvider(), 0));
    }

    /** Shared standard row used by the item bus and the mixed item/fluid assembly. */
    protected Flow createSettingsRow(BigMEOutputProvider<?> provider, int slot) {
        IntSyncValue prioritySyncer = new IntSyncValue(provider::getPriority, provider::setPriority).allowC2S();
        BooleanSyncValue isCaching = new BooleanSyncValue(provider::getCacheMode, provider::setCacheMode).allowC2S();

        Flow mainRow = Flow.row()
            .coverChildren()
            .verticalCenter()
            .collapseDisabledChild();

        mainRow.child(new ItemSlot().slot(new ModularSlot(machine.inventoryHandler, slot).singletonSlotGroup()));
        mainRow.child(
            new ToggleButton().value(isCaching)
                .overlay(GuiTextures.FOLDER)
                .addTooltipLine(StatCollector.translateToLocal("GT5U.hatch.outputme.toggle_caching")));
        mainRow.child(
            new TextFieldWidget().size(75, 14)
                .formatAsInteger(true)
                .value(prioritySyncer)
                .numbersInt(Integer.MIN_VALUE, Integer.MAX_VALUE)
                .setMaxLength(10)
                .tooltip(t -> t.addLine(GuiText.Priority.getLocal()))
                .setEnabledIf(t -> isCaching.getBoolValue())
                .marginLeft(5));

        return mainRow;
    }

    @Override
    protected boolean supportsBottomRowOverlap() {
        return true;
    }
}
