package com.silvia.apeiron.mixin.gregtech.energy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.silvia.apeiron.client.gui.machine.energy.WirelessPowerWidgets;

import gregtech.common.gui.modularui.multiblock.godforge.panel.VoltageConfigPanel;
import gregtech.common.gui.modularui.multiblock.godforge.sync.Modules;
import gregtech.common.gui.modularui.multiblock.godforge.sync.Panels;
import gregtech.common.gui.modularui.multiblock.godforge.sync.SyncHypervisor;
import tectech.thing.metaTileEntity.multi.godforge.MTEBaseModule;

@Mixin(value = VoltageConfigPanel.class, remap = false)
public abstract class GodforgeWirelessGuiMixin {

    @Inject(method = "openModulePanel", at = @At("RETURN"), require = 1)
    private static void apeiron$size(SyncHypervisor hypervisor, Modules<?> module,
        CallbackInfoReturnable<ModularPanel> cir) {
        PanelSyncManager sync = hypervisor.getSyncManager(module, Panels.VOLTAGE_CONFIG);
        BooleanSyncValue installed = WirelessPowerWidgets.installed(sync, hypervisor.getModule(module));
        cir.getReturnValue()
            .size(138, 148)
            .onUpdateListener(panel -> {
                int height = installed.getBoolValue() ? 148 : 98;
                if (panel.getArea().height != height) panel.height(height);
            });
    }

    @Inject(method = "createMaxParallelGroup", at = @At("RETURN"), cancellable = true, require = 1)
    private static void apeiron$parallel(SyncHypervisor hypervisor, Modules<?> module,
        CallbackInfoReturnable<Flow> cir) {
        MTEBaseModule machine = hypervisor.getModule(module);
        PanelSyncManager sync = hypervisor.getSyncManager(module, Panels.VOLTAGE_CONFIG);
        BooleanSyncValue installed = WirelessPowerWidgets.installed(sync, machine);
        Flow nativeGroup = cir.getReturnValue();
        nativeGroup.setEnabledIf(w -> !installed.getBoolValue());
        cir.setReturnValue(
            Flow.column()
                .fullWidth()
                .coverChildren()
                .collapseDisabledChild()
                .child(nativeGroup)
                .child(
                    Flow.column()
                        .fullWidth()
                        .coverChildren()
                        .child(
                            IKey.lang("GTPP.CC.parallel")
                                .asWidget()
                                .height(14))
                        .child(WirelessPowerWidgets.parallel(sync, machine, installed))
                        .setEnabledIf(w -> installed.getBoolValue())));
    }

    @Inject(method = "createVoltageGroup", at = @At("RETURN"), cancellable = true, require = 1)
    private static void apeiron$config(SyncHypervisor hypervisor, Modules<?> module,
        CallbackInfoReturnable<ParentWidget<?>> cir) {
        MTEBaseModule machine = hypervisor.getModule(module);
        PanelSyncManager sync = hypervisor.getSyncManager(module, Panels.VOLTAGE_CONFIG);
        BooleanSyncValue installed = WirelessPowerWidgets.installed(sync, machine);
        ParentWidget<?> nativeGroup = cir.getReturnValue();
        nativeGroup.setEnabledIf(w -> !installed.getBoolValue());
        cir.setReturnValue(
            Flow.column()
                .fullWidth()
                .coverChildren()
                .collapseDisabledChild()
                .child(nativeGroup)
                .child(WirelessPowerWidgets.config(sync, machine, installed)));
    }
}
