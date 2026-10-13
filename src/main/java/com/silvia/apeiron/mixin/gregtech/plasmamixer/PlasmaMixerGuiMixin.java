package com.silvia.apeiron.mixin.gregtech.plasmamixer;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.silvia.apeiron.api.machine.parallel.BigPlasmaMixerController;
import com.silvia.apeiron.client.gui.machine.energy.WirelessPowerWidgets;
import com.silvia.apeiron.math.ScientificInteger;

import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.tileentities.machines.multi.MTETranscendentPlasmaMixer;

@Pseudo
@Mixin(targets = "gregtech.common.gui.modularui.multiblock.MTETranscendentPlasmaMixerGui", remap = false)
public abstract class PlasmaMixerGuiMixin extends MTEMultiBlockBaseGui<MTETranscendentPlasmaMixer> {

    protected PlasmaMixerGuiMixin(MTETranscendentPlasmaMixer machine) {
        super(machine);
    }

    @Inject(method = "registerSyncValues", at = @At("RETURN"), require = 1)
    private void apeiron$settings(PanelSyncManager sync, CallbackInfo ci) {
        BigPlasmaMixerController controller = (BigPlasmaMixerController) (Object) multiblock;
        sync.syncValue(
            "apeiron_plasma_parallel",
            new StringSyncValue(() -> ScientificInteger.format(controller.getPlasmaMixerParallel()), text -> {
                try {
                    controller.setPlasmaMixerParallel(ScientificInteger.nonNegative(text));
                } catch (IllegalArgumentException | ArithmeticException ignored) {}
            }).allowC2S());
        sync.syncValue(
            "apeiron_plasma_duration",
            new StringSyncValue(() -> Integer.toString(controller.getPlasmaMixerDuration()), text -> {
                try {
                    controller.setPlasmaMixerDuration(
                        ScientificInteger.positive(text)
                            .intValueExact());
                } catch (IllegalArgumentException | ArithmeticException ignored) {}
            }).allowC2S());
    }

    @Inject(method = "openParallelSelectPanel", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$panel(PanelSyncManager sync, ModularPanel parent, CallbackInfoReturnable<ModularPanel> cir) {
        cir.setReturnValue(
            new ModularPanel("parallelSelectPanel").size(138, 92)
                .relative(parent)
                .leftRel(1)
                .topRel(0.8f)
                .child(
                    Flow.column()
                        .full()
                        .padding(4)
                        .childPadding(3)
                        .child(
                            IKey.lang("GTPP.CC.parallel")
                                .asWidget()
                                .height(14))
                        .child(
                            apeiron$field(
                                sync,
                                "apeiron_plasma_parallel",
                                true,
                                "apeiron.machine.energy.parallel_hint"))
                        .child(
                            IKey.lang("apeiron.machine.energy.duration_label")
                                .asWidget()
                                .height(14))
                        .child(
                            apeiron$field(
                                sync,
                                "apeiron_plasma_duration",
                                false,
                                "apeiron.machine.plasma_mixer.duration_hint"))));
    }

    private TextFieldWidget apeiron$field(PanelSyncManager sync, String key, boolean zero, String hint) {
        StringSyncValue value = sync.findSyncHandler(key, StringSyncValue.class);
        return new TextFieldWidget().name(key)
            .size(130, 18)
            .value(value)
            .setMaxLength(4096)
            .setPattern(WirelessPowerWidgets.INPUT)
            .acceptsExpressions(false)
            .autoUpdateOnChange(false)
            .setTextAlignment(Alignment.CENTER)
            .setValidator(text -> {
                try {
                    BigInteger parsed = zero ? ScientificInteger.nonNegative(text) : ScientificInteger.positive(text);
                    if (!zero) parsed.intValueExact();
                    return text.trim();
                } catch (IllegalArgumentException | ArithmeticException ignored) {
                    return value.getStringValue();
                }
            })
            .tooltip(t -> t.addLine(IKey.lang(hint)));
    }
}
