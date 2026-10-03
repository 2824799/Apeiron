package com.silvia.apeiron.mixin.gregtech.energy;

import java.util.Collections;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.modularui.common.widget.ChangeableWidget;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.client.gui.machine.energy.WirelessRecipeWidgets;
import com.silvia.apeiron.client.gui.sync.ChunkedNbtSyncWidget;

import appeng.api.storage.data.IAEStack;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class LegacyWirelessRecipeGuiMixin {

    @Unique
    private List<IAEStack<?>> apeiron$outputPreview = Collections.emptyList();

    @Inject(method = "drawTexts", at = @At("RETURN"), require = 1)
    private void apeiron$outputs(DynamicPositionedColumn column, SlotWidget slot, CallbackInfo ci) {
        final MTEMultiBlockBase machine = (MTEMultiBlockBase) (Object) this;
        final ChangeableWidget outputs = new ChangeableWidget(
            () -> WirelessRecipeWidgets.legacy(apeiron$outputPreview, () -> machine.mMaxProgresstime));
        column.widget(
            new ChunkedNbtSyncWidget(
                () -> ((BigWirelessController) this).getWirelessRecipeState()
                    .writeDisplayNBT(),
                snapshot -> {
                    final com.silvia.apeiron.common.machine.output.BigMachineOutputQueue preview = new com.silvia.apeiron.common.machine.output.BigMachineOutputQueue();
                    preview.load(snapshot);
                    apeiron$outputPreview = preview.snapshotOutputs();
                    outputs.notifyChangeNoSync();
                }));
        column.widget(outputs.setEnabled(widget -> !apeiron$outputPreview.isEmpty()));
    }
}
