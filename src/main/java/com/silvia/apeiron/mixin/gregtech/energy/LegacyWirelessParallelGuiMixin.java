package com.silvia.apeiron.mixin.gregtech.energy;

import java.math.BigInteger;
import java.util.Collections;

import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.textfield.NumericWidget;
import com.gtnewhorizons.modularui.common.widget.textfield.TextFieldWidget;
import com.llamalad7.mixinextras.sugar.Local;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;

import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Gives the MUI1 power panel the same zero/unlimited and exact positive cap as the MUI2 panel. */
@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class LegacyWirelessParallelGuiMixin {

    @Unique
    private boolean apeiron$energyInstalledForGui;
    @Unique
    private boolean apeiron$ultimateForGui;

    @Inject(
        method = "createPowerPanel",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizons/modularui/api/screen/ModularWindow$Builder;build()Lcom/gtnewhorizons/modularui/api/screen/ModularWindow;"),
        require = 1)
    private void apeiron$legacyParallelField(EntityPlayer player, CallbackInfoReturnable<ModularWindow> cir,
        @Local ModularWindow.Builder builder, @Local NumericWidget textField) {
        MTEMultiBlockBase machine = (MTEMultiBlockBase) (Object) this;
        if (InfiniteEnergyHatches.find(machine) == null) return;
        BigWirelessController controller = (BigWirelessController) (Object) this;
        builder.widget(
            new FakeSyncWidget.BooleanSyncer(
                () -> InfiniteEnergyHatches.find(machine) != null,
                value -> apeiron$energyInstalledForGui = value));
        builder.widget(
            new FakeSyncWidget.BooleanSyncer(
                () -> InfiniteEnergyHatches.isUltimate(machine),
                value -> apeiron$ultimateForGui = value));
        textField.setEnabled(widget -> !apeiron$energyInstalledForGui);
        builder.widget(
            new TextFieldWidget()
                .setGetter(
                    () -> com.silvia.apeiron.math.ScientificInteger.format(
                        controller.getWirelessRecipeState()
                            .getParallelSettingBig()))
                .setSetter(text -> {
                    if (InfiniteEnergyHatches.find(machine) == null) return;
                    try {
                        BigInteger count = com.silvia.apeiron.math.ScientificInteger.nonNegative(text);
                        if (count.signum() >= 0) {
                            controller.getWirelessRecipeState()
                                .setParallelSettingBig(count);
                            machine.markDirty();
                        }
                    } catch (IllegalArgumentException | ArithmeticException ignored) {}
                })
                .setPattern(com.silvia.apeiron.client.gui.machine.energy.WirelessPowerWidgets.INPUT)
                .setMaxLength(4096)
                .setValidator(text -> {
                    try {
                        com.silvia.apeiron.math.ScientificInteger.nonNegative(text);
                        return text.trim();
                    } catch (IllegalArgumentException | ArithmeticException error) {
                        return com.silvia.apeiron.math.ScientificInteger.format(
                            controller.getWirelessRecipeState()
                                .getParallelSettingBig());
                    }
                })
                .setPos(12, 40)
                .setSize(96, 18)
                .setBackground(GTUITextures.BACKGROUND_TEXT_FIELD)
                .setEnabled(widget -> apeiron$energyInstalledForGui)
                .dynamicTooltip(
                    () -> Collections.singletonList(
                        net.minecraft.util.StatCollector.translateToLocal("apeiron.machine.energy.parallel_hint"))));
        builder.widget(
            new com.gtnewhorizons.modularui.common.widget.TextWidget()
                .dynamicString(
                    () -> net.minecraft.util.StatCollector.translateToLocal(
                        apeiron$ultimateForGui ? "apeiron.machine.energy.duration_label"
                            : "apeiron.machine.energy.voltage_label"))
                .setPos(3, 80)
                .setSize(114, 14)
                .setEnabled(w -> apeiron$energyInstalledForGui));
        builder.widget(new TextFieldWidget().setGetter(() -> {
            com.silvia.apeiron.common.machine.parallel.WirelessRecipeState state = controller.getWirelessRecipeState();
            return Long.toString(
                InfiniteEnergyHatches.isUltimate(machine) ? state.getTargetDuration() : state.getVoltageSetting());
        })
            .setSetter(text -> {
                if (InfiniteEnergyHatches.find(machine) == null) return;
                try {
                    BigInteger n = com.silvia.apeiron.math.ScientificInteger.positive(text);
                    if (InfiniteEnergyHatches.isUltimate(machine)) controller.getWirelessRecipeState()
                        .setTargetDuration(n.intValueExact());
                    else controller.getWirelessRecipeState()
                        .setVoltageSetting(n.longValueExact());
                    machine.markDirty();
                } catch (IllegalArgumentException | ArithmeticException ignored) {}
            })
            .setPattern(com.silvia.apeiron.client.gui.machine.energy.WirelessPowerWidgets.INPUT)
            .setMaxLength(4096)
            .setPos(12, 96)
            .setSize(96, 18)
            .setBackground(GTUITextures.BACKGROUND_TEXT_FIELD)
            .setEnabled(w -> apeiron$energyInstalledForGui)
            .dynamicTooltip(
                () -> Collections.singletonList(
                    net.minecraft.util.StatCollector.translateToLocal(
                        apeiron$ultimateForGui ? "apeiron.machine.energy.duration_hint"
                            : "apeiron.machine.energy.voltage_hint"))));
    }

    @Inject(method = "createMaxParallelCheckBox", at = @At("RETURN"), require = 1)
    private void apeiron$disableLegacyMaximum(NumericWidget field, CallbackInfoReturnable<ButtonWidget> cir) {
        cir.getReturnValue()
            .setEnabled(widget -> !apeiron$energyInstalledForGui);
    }
}
