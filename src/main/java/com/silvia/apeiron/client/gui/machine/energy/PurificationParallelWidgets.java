package com.silvia.apeiron.client.gui.machine.energy;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.silvia.apeiron.api.machine.parallel.BigPurificationUnit;
import com.silvia.apeiron.common.machine.parallel.PurificationParallels;
import com.silvia.apeiron.math.ScientificInteger;

import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitBase;

/** Both sides build the same handlers even when only the server knows the linked plant. */
public final class PurificationParallelWidgets {

    public static final String INSTALLED = "apeiron_purificationEnergyInstalled";
    public static final String VALUE = "apeiron_purificationParallel";

    private PurificationParallelWidgets() {}

    public static BooleanSyncValue installed(PanelSyncManager sync, MTEPurificationUnitBase<?> unit) {
        return sync.getOrCreateSyncHandler(
            INSTALLED,
            0,
            BooleanSyncValue.class,
            () -> new BooleanSyncValue(() -> PurificationParallels.source(unit) != null));
    }

    public static ModularPanel panel(PanelSyncManager sync, ModularPanel parent, MTEPurificationUnitBase<?> unit) {
        BigPurificationUnit exact = (BigPurificationUnit) unit;
        StringSyncValue value = new StringSyncValue(
            () -> ScientificInteger.format(exact.getPurificationParallelSettingBig()),
            text -> {
                if (PurificationParallels.source(unit) == null) return;
                try {
                    exact.setPurificationParallelSettingBig(ScientificInteger.nonNegative(text));
                    unit.markDirty();
                } catch (IllegalArgumentException | ArithmeticException ignored) {}
            }).allowC2S();
        sync.syncValue(VALUE, value);
        return new ModularPanel("apeironPurificationParallel").size(158, 52)
            .relative(parent)
            .leftRel(1)
            .topRel(0.8f)
            .child(
                Flow.column()
                    .full()
                    .paddingTop(4)
                    .child(
                        IKey.lang("GTPP.CC.parallel")
                            .asWidget()
                            .marginBottom(4))
                    .child(
                        new TextFieldWidget().size(150, 18)
                            .name(VALUE)
                            .value(value)
                            .setTextAlignment(Alignment.CENTER)
                            .setMaxLength(4096)
                            .setPattern(WirelessPowerWidgets.INPUT)
                            .acceptsExpressions(false)
                            .autoUpdateOnChange(false)
                            .setValidator(text -> {
                                try {
                                    ScientificInteger.nonNegative(text);
                                    return text.trim();
                                } catch (IllegalArgumentException | ArithmeticException invalid) {
                                    return value.getStringValue();
                                }
                            })
                            .tooltip(t -> t.addLine(IKey.lang("apeiron.machine.energy.parallel_hint")))));
    }
}
