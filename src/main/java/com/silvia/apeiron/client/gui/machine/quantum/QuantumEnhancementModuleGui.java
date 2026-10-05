package com.silvia.apeiron.client.gui.machine.quantum;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.silvia.apeiron.common.machine.quantum.MTEQuantumEnhancementModule;

import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;

public final class QuantumEnhancementModuleGui extends MTEHatchBaseGui<MTEQuantumEnhancementModule> {

    public QuantumEnhancementModuleGui(MTEQuantumEnhancementModule machine) {
        super(machine);
    }

    @Override
    protected int getBasePanelWidth() {
        return 248;
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
        return super.createContentSection(panel, sync).child(
            Flow.column()
                .coverChildren()
                .child(
                    IKey.lang("apeiron.machine.quantum_enhancement_module.outputs")
                        .asWidget())
                .child(
                    IKey.lang("apeiron.machine.quantum_enhancement_module.catalyst")
                        .asWidget())
                .child(
                    IKey.lang("apeiron.machine.quantum_enhancement_module.power")
                        .asWidget()));
    }
}
