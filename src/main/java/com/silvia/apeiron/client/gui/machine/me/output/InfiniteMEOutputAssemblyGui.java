// SPDX-License-Identifier: LGPL-3.0-only
// Extends the GT5-Unofficial output GUI ported by Apeiron.
package com.silvia.apeiron.client.gui.machine.me.output;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;

/** Both channels are configured in the placed assembly's GUI and use its two real inventory slots. */
public final class InfiniteMEOutputAssemblyGui extends BoundlessMEOutputBusGui {

    private final MTEInfiniteMEOutputAssembly assembly;

    public InfiniteMEOutputAssemblyGui(MTEInfiniteMEOutputAssembly machine) {
        super(machine);
        assembly = machine;
    }

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager sync) {
        return getOverlappingEmptyContent().child(
            Flow.column()
                .coverChildren()
                .child(createSettingsRow(assembly.getProvider(), 0))
                .child(createSettingsRow(assembly.getFluidProvider(), 1).marginTop(2)));
    }

    @Override
    protected int getBasePanelHeight() {
        return 192;
    }

}
