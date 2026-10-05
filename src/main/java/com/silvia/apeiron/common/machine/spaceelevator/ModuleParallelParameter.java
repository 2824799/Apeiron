package com.silvia.apeiron.common.machine.spaceelevator;

import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.function.Function;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.silvia.apeiron.client.gui.machine.energy.WirelessPowerWidgets;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.math.ScientificInteger;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.gui.modularui.widget.WidgetConfigurator;
import gregtech.common.gui.modularui.widget.settings.SettingsPanelBuilder;
import tectech.thing.metaTileEntity.multi.base.parameter.IntegerParameter;
import tectech.thing.metaTileEntity.multi.base.parameter.Parameter;

/** Native modules keep their normal cap until an ultimate parent unlocks an exact integer setting. */
public final class ModuleParallelParameter extends IntegerParameter {

    private final MTEMultiBlockBase module;
    private final int minimum;
    private final int maximum;
    private BigInteger exact;

    public ModuleParallelParameter(MTEMultiBlockBase module, IntegerParameter original) {
        super(
            original.getValue(),
            original.getLangKey(),
            original.getNbtKey(),
            () -> readBound(original, "getMin", 0),
            () -> readBound(original, "getMax", Integer.MAX_VALUE),
            original.getLangArgs());
        this.module = module;
        this.minimum = readBound(original, "getMin", 0);
        this.maximum = readBound(original, "getMax", Integer.MAX_VALUE);
        this.exact = BigInteger.valueOf(original.getValue());
    }

    @SuppressWarnings("rawtypes")
    private static int readBound(Parameter parameter, String methodName, int fallback) {
        try {
            Method method = parameter.getClass()
                .getMethod(methodName);
            Object value = method.invoke(parameter);
            return value instanceof Number ? ((Number) value).intValue() : fallback;
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return fallback;
        }
    }

    public BigInteger getBig() {
        BigInteger value = exact == null ? BigInteger.valueOf(minimum) : exact;
        return InfiniteEnergyHatches.isUltimate(module) ? value : value.min(BigInteger.valueOf(maximum));
    }

    public void setBig(BigInteger value) {
        if (value.signum() < 0) throw new IllegalArgumentException("Negative parallel");
        if (!InfiniteEnergyHatches.isUltimate(module)) value = value.max(BigInteger.valueOf(minimum))
            .min(BigInteger.valueOf(maximum));
        exact = value;
        module.markDirty();
    }

    @Override
    public Integer getValue() {
        return getBig().min(BigInteger.valueOf(Integer.MAX_VALUE))
            .intValueExact();
    }

    @Override
    public void setValue(Integer value) {
        setBig(BigInteger.valueOf(value));
    }

    @Override
    public Integer validate(Integer value) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    @Override
    public void saveNBT(NBTTagCompound tag) {
        tag.setInteger(getNbtKey(), getValue());
        tag.setString(getNbtKey() + "ApeironBig", exact.toString());
    }

    @Override
    public void loadNBT(NBTTagCompound tag) {
        if (tag.hasKey(getNbtKey() + "ApeironBig"))
            exact = ScientificInteger.nonNegative(tag.getString(getNbtKey() + "ApeironBig"));
        else if (tag.hasKey(getNbtKey())) exact = BigInteger.valueOf(validate(tag.getInteger(getNbtKey())));
    }

    @Override
    public void registerSyncValue(PanelSyncManager syncManager, String prefix) {
        String key = prefix + getNbtKey();
        syncManager.syncValue(key, createExactSyncValue());
    }

    /** Client structure lists are empty; keep the received text exact and validate edits on the server. */
    public StringSyncValue createExactSyncValue() {
        return new StringSyncValue(() -> ScientificInteger.format(getBig())) {

            @Override
            public void readOnServer(int id, PacketBuffer buffer) throws IOException {
                if (id != SYNC_VALUE) return;
                String text = deserialize(buffer);
                try {
                    setBig(ScientificInteger.nonNegative(text));
                } catch (IllegalArgumentException | ArithmeticException ignored) {}
                setStringValue(ScientificInteger.format(getBig()), false, false);
            }
        }.allowC2S();
    }

    private String validateText(String text) {
        try {
            return ScientificInteger.format(ScientificInteger.nonNegative(text));
        } catch (IllegalArgumentException | ArithmeticException error) {
            return ScientificInteger.format(getBig());
        }
    }

    /** The old TecTech GUI builds integer editors itself instead of dispatching to addToSettingsPanel. */
    public TextFieldWidget createExactEditor() {
        return new TextFieldWidget().name("apeiron_module_parallel")
            .size(120, 18)
            .value(createExactSyncValue())
            .setMaxLength(4096)
            .setPattern(WirelessPowerWidgets.INPUT)
            .acceptsExpressions(false)
            .autoUpdateOnChange(false)
            .setValidator(this::validateText);
    }

    @Override
    public void addToSettingsPanel(SettingsPanelBuilder builder, IKey label, WidgetConfigurator<?> configure,
        String prefix, Function<Parameter<?, ?>, WidgetConfigurator<?>> configurator) {
        builder.addStringEditor(label, createExactSyncValue(), (panel, syncManager, widget) -> {
            widget.setMaxLength(4096)
                .setPattern(WirelessPowerWidgets.INPUT)
                .acceptsExpressions(false)
                .autoUpdateOnChange(false)
                .setValidator(this::validateText);
        });
    }
}
