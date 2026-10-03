package com.silvia.apeiron.mixin.gregtech.energy;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.client.gui.machine.energy.WirelessPowerWidgets;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

@Mixin(value = MTEMultiBlockBaseGui.class, remap = false)
public abstract class WirelessParallelGuiMixin {

    @Shadow
    @Final
    protected MTEMultiBlockBase multiblock;

    @Inject(method = "showMaxParallelRow", at = @At("RETURN"), cancellable = true, require = 1)
    private void apeiron$showRow(CallbackInfoReturnable<Boolean> cir) {
        if (multiblock instanceof BigWirelessController && InfiniteEnergyHatches.find(multiblock) != null)
            cir.setReturnValue(true);
    }

    @Inject(method = "openPowerControlPanel", at = @At("RETURN"), require = 1)
    private void apeiron$panel(PanelSyncManager sync, ModularPanel parent, CallbackInfoReturnable<ModularPanel> cir) {
        if (multiblock instanceof BigWirelessController && InfiniteEnergyHatches.find(multiblock) != null)
            cir.getReturnValue()
                .size(120, 170);
    }

    @Inject(method = "makeParallelConfigurator", at = @At("RETURN"), cancellable = true, require = 1)
    private void apeiron$exactRow(PanelSyncManager sync, CallbackInfoReturnable<IWidget> cir) {
        if (!(multiblock instanceof BigWirelessController) || InfiniteEnergyHatches.find(multiblock) == null) return;
        BooleanSyncValue installed = WirelessPowerWidgets.installed(sync, multiblock);
        IWidget nativeRow = cir.getReturnValue();
        cir.setReturnValue(
            Flow.column()
                .fullWidth()
                .coverChildren()
                .collapseDisabledChild()
                .child(
                    new com.cleanroommc.modularui.widget.ParentWidget<>().fullWidth()
                        .height(22)
                        .child(nativeRow)
                        .setEnabledIf(w -> !installed.getBoolValue()))
                .child(WirelessPowerWidgets.parallel(sync, multiblock, installed))
                .child(WirelessPowerWidgets.config(sync, multiblock, installed)));
    }
}
