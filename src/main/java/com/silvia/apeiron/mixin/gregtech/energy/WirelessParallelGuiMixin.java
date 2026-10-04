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
import com.silvia.apeiron.client.gui.machine.energy.WirelessPowerWidgets;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;

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

    @Inject(method = "openPowerControlPanel", at = @At("HEAD"), require = 1)
    private void apeiron$initializeNativeParallel(PanelSyncManager sync, ModularPanel parent,
        CallbackInfoReturnable<ModularPanel> cir) {
        // MUI2 copies this value into its first sync; unlike MUI1, it never initializes the locked maximum.
        if (InfiniteEnergyHatches.find(multiblock) == null && multiblock.isAlwaysMaxParallel()) {
            int maximum = multiblock.getMaxParallelRecipes();
            if (maximum > 0) multiblock.setPowerPanelMaxParallel(maximum);
        }
    }

    @Inject(method = "openPowerControlPanel", at = @At("RETURN"), require = 1)
    private void apeiron$panel(PanelSyncManager sync, ModularPanel parent, CallbackInfoReturnable<ModularPanel> cir) {
        if (!(multiblock instanceof BigWirelessController)) return;
        BooleanSyncValue installed = WirelessPowerWidgets.installed(sync, multiblock);
        cir.getReturnValue()
            .onUpdateListener(panel -> {
                int height = installed.getBoolValue() ? 170 : 130;
                if (panel.getArea().height != height) panel.height(height);
            });
    }

    @Inject(method = "makeParallelConfigurator", at = @At("RETURN"), cancellable = true, require = 1)
    private void apeiron$exactRow(PanelSyncManager sync, CallbackInfoReturnable<IWidget> cir) {
        if (!(multiblock instanceof BigWirelessController)) return;
        // The client has no controller hatch list. Both sides must build all sync handlers and
        // widgets; only their visibility depends on the server's synchronized installation state.
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
