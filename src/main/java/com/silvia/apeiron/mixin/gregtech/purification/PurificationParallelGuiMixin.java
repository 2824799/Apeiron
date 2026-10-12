package com.silvia.apeiron.mixin.gregtech.purification;

import org.spongepowered.asm.mixin.Mixin;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.silvia.apeiron.client.gui.machine.energy.PurificationParallelWidgets;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.multiblock.MTEPurificationUnitBaseGui;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitBase;

@Mixin(value = MTEPurificationUnitBaseGui.class, remap = false)
public abstract class PurificationParallelGuiMixin extends MTEMultiBlockBaseGui<MTEPurificationUnitBase<?>> {

    protected PurificationParallelGuiMixin(MTEPurificationUnitBase<?> multiblock) {
        super(multiblock);
    }

    @WrapMethod(method = "createParallelButton")
    private IWidget apeiron$parallelButton(PanelSyncManager sync, ModularPanel parent, Operation<IWidget> original) {
        IWidget nativeButton = original.call(sync, parent);
        BooleanSyncValue installed = PurificationParallelWidgets.installed(sync, multiblock);
        IPanelHandler panel = sync.syncedPanel(
            "apeironPurificationParallel",
            true,
            (panelSync, handler) -> PurificationParallelWidgets.panel(panelSync, parent, multiblock));
        return new ParentWidget<>().size(18)
            .child(
                new ParentWidget<>().size(18)
                    .child(nativeButton)
                    .setEnabledIf(w -> !installed.getBoolValue()))
            .child(
                new ButtonWidget<>().size(18)
                    .name("apeiron_purificationParallelButton")
                    .overlay(GTGuiTextures.OVERLAY_BUTTON_BATCH_MODE_ON)
                    .tooltip(
                        t -> t.addLine(net.minecraft.util.StatCollector.translateToLocal("GT5U.tpm.parallelwindow")))
                    .onMousePressed(mouse -> {
                        panel.togglePanel();
                        return true;
                    })
                    .setEnabledIf(w -> installed.getBoolValue()));
    }
}
