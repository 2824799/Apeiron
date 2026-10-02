package com.silvia.apeiron.mixin.gregtech.energy;

import java.math.BigInteger;
import java.util.Collections;
import java.util.regex.Pattern;

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

    @Inject(
        method = "createPowerPanel",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizons/modularui/api/screen/ModularWindow$Builder;build()Lcom/gtnewhorizons/modularui/api/screen/ModularWindow;"),
        require = 1)
    private void apeiron$legacyParallelField(EntityPlayer player, CallbackInfoReturnable<ModularWindow> cir,
        @Local ModularWindow.Builder builder, @Local NumericWidget textField) {
        MTEMultiBlockBase machine = (MTEMultiBlockBase) (Object) this;
        BigWirelessController controller = (BigWirelessController) (Object) this;
        builder.widget(
            new FakeSyncWidget.BooleanSyncer(
                () -> InfiniteEnergyHatches.find(machine) != null,
                value -> apeiron$energyInstalledForGui = value));
        textField.setEnabled(widget -> !apeiron$energyInstalledForGui);
        builder.widget(
            new TextFieldWidget().setGetter(
                () -> controller.getWirelessRecipeState()
                    .getParallelSettingBig()
                    .toString())
                .setSetter(text -> {
                    if (InfiniteEnergyHatches.find(machine) == null) return;
                    try {
                        BigInteger count = new BigInteger(text);
                        if (count.signum() >= 0) {
                            controller.getWirelessRecipeState()
                                .setParallelSettingBig(count);
                            machine.markDirty();
                        }
                    } catch (NumberFormatException ignored) {}
                })
                .setPattern(Pattern.compile("[0-9]*"))
                .setMaxLength(32767)
                .setValidator(text -> {
                    try {
                        return new BigInteger(text).max(BigInteger.ZERO)
                            .toString();
                    } catch (NumberFormatException error) {
                        return controller.getWirelessRecipeState()
                            .getParallelSettingBig()
                            .toString();
                    }
                })
                .setPos(12, 40)
                .setSize(96, 18)
                .setBackground(GTUITextures.BACKGROUND_TEXT_FIELD)
                .setEnabled(widget -> apeiron$energyInstalledForGui)
                .dynamicTooltip(
                    () -> Collections.singletonList(
                        net.minecraft.util.StatCollector.translateToLocal("apeiron.machine.energy.parallel_hint"))));
    }

    @Inject(method = "createMaxParallelCheckBox", at = @At("RETURN"), require = 1)
    private void apeiron$disableLegacyMaximum(NumericWidget field, CallbackInfoReturnable<ButtonWidget> cir) {
        cir.getReturnValue()
            .setEnabled(widget -> !apeiron$energyInstalledForGui);
    }
}
