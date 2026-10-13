package com.silvia.apeiron.mixin.gtnl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.silvia.apeiron.client.gui.machine.energy.WirelessPowerWidgets;

import gregtech.common.gui.modularui.multiblock.godforge.MTEBaseModuleGui;
import tectech.thing.metaTileEntity.multi.godforge.MTEBaseModule;

/** The shared GTNL module panel is independent of GregTech's VoltageConfigPanel. */
@Pseudo
@Mixin(targets = "com.science.gtnl.common.gui.modularui.FOGModuleGui", remap = false)
public abstract class GtnlGodforgeGuiMixin extends MTEBaseModuleGui<MTEBaseModule> {

    @Unique
    private PanelSyncManager apeiron$powerSync;

    protected GtnlGodforgeGuiMixin(MTEBaseModule multiblock) {
        super(multiblock);
    }

    @Inject(method = "createVoltageConfigPanel", at = @At("HEAD"), require = 1)
    private void apeiron$sync(PanelSyncManager sync, CallbackInfoReturnable<ModularPanel> cir) {
        apeiron$powerSync = sync;
    }

    @Inject(method = "createVoltageConfigPanel", at = @At("RETURN"), require = 1)
    private void apeiron$size(PanelSyncManager sync, CallbackInfoReturnable<ModularPanel> cir) {
        BooleanSyncValue installed = WirelessPowerWidgets.installed(sync, multiblock);
        cir.getReturnValue()
            .size(138, 148)
            .onUpdateListener(panel -> {
                int height = installed.getBoolValue() ? 148 : 98;
                if (panel.getArea().height != height) panel.height(height);
            });
    }

    @Inject(method = "createMaxParallelGroup", at = @At("RETURN"), cancellable = true, require = 1)
    private void apeiron$parallel(IntSyncValue max, IntSyncValue configured, BooleanSyncValue always,
        CallbackInfoReturnable<Flow> cir) {
        BooleanSyncValue installed = WirelessPowerWidgets.installed(apeiron$powerSync, multiblock);
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
                        .child(WirelessPowerWidgets.parallel(apeiron$powerSync, multiblock, installed))
                        .setEnabledIf(w -> installed.getBoolValue())));
    }

    @Inject(method = "createVoltageGroup", at = @At("RETURN"), cancellable = true, require = 1)
    private void apeiron$config(LongSyncValue voltage, BooleanSyncValue unlocked,
        CallbackInfoReturnable<ParentWidget<?>> cir) {
        BooleanSyncValue installed = WirelessPowerWidgets.installed(apeiron$powerSync, multiblock);
        ParentWidget<?> nativeGroup = cir.getReturnValue();
        nativeGroup.setEnabledIf(w -> !installed.getBoolValue());
        cir.setReturnValue(
            Flow.column()
                .fullWidth()
                .coverChildren()
                .collapseDisabledChild()
                .child(nativeGroup)
                .child(WirelessPowerWidgets.config(apeiron$powerSync, multiblock, installed)));
    }
}
