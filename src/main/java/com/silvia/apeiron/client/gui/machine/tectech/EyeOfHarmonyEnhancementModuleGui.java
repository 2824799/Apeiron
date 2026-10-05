package com.silvia.apeiron.client.gui.machine.tectech;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.silvia.apeiron.common.machine.tectech.MTEEyeOfHarmonyEnhancementModule;

import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;

public final class EyeOfHarmonyEnhancementModuleGui extends MTEHatchBaseGui<MTEEyeOfHarmonyEnhancementModule> {

    public EyeOfHarmonyEnhancementModuleGui(MTEEyeOfHarmonyEnhancementModule machine) {
        super(machine);
    }

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager sync) {
        IntSyncValue duration = new IntSyncValue(machine::getDuration, machine::setDuration).allowC2S();
        DoubleSyncValue successChance = new DoubleSyncValue(machine::getSuccessChance, machine::setSuccessChance)
            .allowC2S();
        return super.createContentSection(panel, sync).child(
            Flow.column()
                .coverChildren()
                .child(
                    IKey.lang("apeiron.machine.eye_of_harmony_enhancement_module.duration")
                        .asWidget())
                .child(
                    new TextFieldWidget().size(120, 18)
                        .value(duration)
                        .numbersInt(1, Integer.MAX_VALUE))
                .child(
                    IKey.lang("apeiron.machine.eye_of_harmony_enhancement_module.success_chance")
                        .asWidget())
                .child(
                    new TextFieldWidget().size(120, 18)
                        .value(successChance)
                        .numbersDouble(value -> Math.max(0.0D, Math.min(1.0D, value)))));
    }
}
