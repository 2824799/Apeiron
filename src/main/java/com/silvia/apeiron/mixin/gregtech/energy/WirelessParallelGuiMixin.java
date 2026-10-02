package com.silvia.apeiron.mixin.gregtech.energy;

import java.math.BigInteger;
import java.util.regex.Pattern;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/** The normal power-panel row becomes an exact decimal field when an Infinite Energy Hatch is installed. */
@Mixin(value = MTEMultiBlockBaseGui.class, remap = false)
public abstract class WirelessParallelGuiMixin {

    @Shadow
    @Final
    protected MTEMultiBlockBase multiblock;

    @Inject(method = "showMaxParallelRow", at = @At("RETURN"), cancellable = true, require = 1)
    private void apeiron$showRow(CallbackInfoReturnable<Boolean> cir) {
        // Both sides must build the same sync layout even before the client knows the structure's hatches.
        if (multiblock instanceof BigWirelessController) cir.setReturnValue(true);
    }

    @Inject(method = "makeParallelConfigurator", at = @At("RETURN"), cancellable = true, require = 1)
    private void apeiron$exactRow(PanelSyncManager sync, CallbackInfoReturnable<IWidget> cir) {
        if (!(multiblock instanceof BigWirelessController)) return;
        BigWirelessController controller = (BigWirelessController) multiblock;
        BooleanSyncValue installed = new BooleanSyncValue(() -> InfiniteEnergyHatches.find(multiblock) != null);
        sync.syncValue("apeiron_infinite_energy_installed", installed);
        StringSyncValue value = new StringSyncValue(
            () -> controller.getWirelessRecipeState()
                .getParallelSettingBig()
                .toString(),
            text -> {
                if (InfiniteEnergyHatches.find(multiblock) == null) return;
                try {
                    BigInteger count = new BigInteger(text);
                    if (count.signum() < 0) return;
                    controller.getWirelessRecipeState()
                        .setParallelSettingBig(count);
                    multiblock.markDirty();
                } catch (NumberFormatException ignored) {}
            }).allowC2S();
        IWidget normal = cir.getReturnValue();
        cir.setReturnValue(
            Flow.column()
                .fullWidth()
                .coverChildren()
                .child(
                    new ParentWidget<>().fullWidth()
                        .height(22)
                        .child(normal)
                        .setEnabledIf(w -> !installed.getBoolValue()))
                .child(
                    new TextFieldWidget().size(140, 18)
                        .value(value)
                        .setMaxLength(32767)
                        .setPattern(Pattern.compile("[0-9]*"))
                        .acceptsExpressions(false)
                        .autoUpdateOnChange(false)
                        .setValidator(text -> {
                            try {
                                return new BigInteger(text).max(BigInteger.ZERO)
                                    .toString();
                            } catch (NumberFormatException error) {
                                return value.getStringValue();
                            }
                        })
                        .tooltip(t -> t.addLine(IKey.lang("apeiron.machine.energy.parallel_hint")))
                        .setEnabledIf(w -> installed.getBoolValue())));
    }
}
