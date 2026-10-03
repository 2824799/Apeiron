package com.silvia.apeiron.client.gui.machine.energy;

import java.math.BigInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment.MainAxis;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.math.ScientificInteger;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Scientific input uses exact integers and a field that fits inside both power panels. */
public final class WirelessPowerWidgets {

    public static final Pattern INPUT = Pattern.compile("[0-9eE+.\\-]*");

    private WirelessPowerWidgets() {}

    public static BooleanSyncValue installed(PanelSyncManager sync, MTEMultiBlockBase machine) {
        BooleanSyncValue value = new BooleanSyncValue(() -> InfiniteEnergyHatches.find(machine) != null);
        sync.syncValue("apeiron_energyInstalled", value);
        return value;
    }

    public static Flow parallel(PanelSyncManager sync, MTEMultiBlockBase machine, BooleanSyncValue installed) {
        WirelessRecipeState state = ((BigWirelessController) machine).getWirelessRecipeState();
        return field(
            sync,
            "apeiron_parallel",
            machine,
            installed,
            state::getParallelSettingBig,
            state::setParallelSettingBig,
            true,
            "apeiron.machine.energy.parallel_hint");
    }

    public static Flow config(PanelSyncManager sync, MTEMultiBlockBase machine, BooleanSyncValue installed) {
        WirelessRecipeState state = ((BigWirelessController) machine).getWirelessRecipeState();
        BooleanSyncValue ultimate = new BooleanSyncValue(() -> InfiniteEnergyHatches.isUltimate(machine));
        sync.syncValue("apeiron_ultimateEnergy", ultimate);
        return Flow.column()
            .fullWidth()
            .coverChildren()
            .collapseDisabledChild()
            .child(
                IKey.dynamic(
                    () -> net.minecraft.util.StatCollector.translateToLocal(
                        ultimate.getBoolValue() ? "apeiron.machine.energy.duration_label"
                            : "apeiron.machine.energy.voltage_label"))
                    .asWidget()
                    .height(14))
            .child(
                field(
                    sync,
                    "apeiron_voltage",
                    machine,
                    installed,
                    () -> BigInteger.valueOf(state.getVoltageSetting()),
                    n -> state.setVoltageSetting(n.longValueExact()),
                    false,
                    "apeiron.machine.energy.voltage_hint").setEnabledIf(w -> !ultimate.getBoolValue()))
            .child(
                field(
                    sync,
                    "apeiron_duration",
                    machine,
                    installed,
                    () -> BigInteger.valueOf(state.getTargetDuration()),
                    n -> state.setTargetDuration(n.intValueExact()),
                    false,
                    "apeiron.machine.energy.duration_hint").setEnabledIf(w -> ultimate.getBoolValue()))
            .setEnabledIf(w -> installed.getBoolValue());
    }

    private static Flow field(PanelSyncManager sync, String key, MTEMultiBlockBase machine, BooleanSyncValue installed,
        Supplier<BigInteger> getter, Consumer<BigInteger> setter, boolean zero, String hint) {
        StringSyncValue value = new StringSyncValue(() -> ScientificInteger.format(getter.get()), text -> {
            if (InfiniteEnergyHatches.find(machine) == null) return;
            try {
                setter.accept(zero ? ScientificInteger.nonNegative(text) : ScientificInteger.positive(text));
                machine.markDirty();
            } catch (IllegalArgumentException | ArithmeticException ignored) {}
        }).allowC2S();
        sync.syncValue(key, value);
        TextFieldWidget field = new TextFieldWidget().size(96, 18)
            .value(value)
            .setMaxLength(4096)
            .setPattern(INPUT)
            .acceptsExpressions(false)
            .autoUpdateOnChange(false)
            .setValidator(text -> {
                try {
                    BigInteger n = zero ? ScientificInteger.nonNegative(text) : ScientificInteger.positive(text);
                    if (key.endsWith("voltage")) n.longValueExact();
                    if (key.endsWith("duration")) n.intValueExact();
                    return text.trim();
                } catch (IllegalArgumentException | ArithmeticException error) {
                    return value.getStringValue();
                }
            })
            .tooltip(t -> t.addLine(IKey.lang(hint)));
        return Flow.row()
            .fullWidth()
            .height(22)
            .mainAxisAlignment(MainAxis.CENTER)
            .child(field)
            .setEnabledIf(w -> installed.getBoolValue());
    }
}
