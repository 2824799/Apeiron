package com.silvia.apeiron.client.gui.machine.me.input;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.silvia.apeiron.common.machine.me.circuit.MTEInfiniteProgrammingCircuitProvider;

import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;

public final class InfiniteCircuitProviderGui extends MTEHatchBaseGui<MTEInfiniteProgrammingCircuitProvider> {

    public InfiniteCircuitProviderGui(MTEInfiniteProgrammingCircuitProvider machine) {
        super(machine);
    }

    @Override
    protected int getBasePanelWidth() {
        return 184;
    }

    @Override
    protected int getBasePanelHeight() {
        return 296;
    }

    @Override
    protected boolean doesAddCircuitSlot() {
        return false;
    }

    @Override
    protected boolean supportsFluidScreen() {
        return false;
    }

    @Override
    protected boolean supportsFluidIOColumn() {
        return false;
    }

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager sync) {
        sync.registerSlotGroup("apeiron_circuit_samples", 9);
        return super.createContentSection(panel, sync).child(
            new Grid().coverChildren()
                .gridOfWidthHeight(
                    9,
                    9,
                    (x, y, index) -> new ItemSlot()
                        .slot(new ModularSlot(machine.inventoryHandler, index).slotGroup("apeiron_circuit_samples"))));
    }
}
