package com.silvia.apeiron.mixin.gregtech.energy;

import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.modularui.common.widget.ChangeableWidget;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.client.gui.machine.energy.WirelessRecipeWidgets;

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
            new FakeSyncWidget.ListSyncer<NBTTagCompound>(
                () -> Collections.singletonList(
                    ((BigWirelessController) this).getWirelessRecipeState()
                        .writeDisplayNBT()),
                values -> {
                    final com.silvia.apeiron.common.machine.output.BigMachineOutputQueue preview = new com.silvia.apeiron.common.machine.output.BigMachineOutputQueue();
                    if (!values.isEmpty()) preview.load(values.get(0));
                    apeiron$outputPreview = preview.snapshotOutputs();
                    outputs.notifyChangeNoSync();
                },
                (packet, tag) -> {
                    try {
                        com.cleanroommc.modularui.utils.serialization.ByteBufAdapters.NBT.serialize(packet, tag);
                    } catch (java.io.IOException error) {
                        throw new IllegalStateException("Cannot synchronize recipe display", error);
                    }
                },
                packet -> {
                    try {
                        return com.cleanroommc.modularui.utils.serialization.ByteBufAdapters.NBT.deserialize(packet);
                    } catch (java.io.IOException error) {
                        throw new IllegalStateException("Invalid recipe display", error);
                    }
                }));
        column.widget(outputs.setEnabled(widget -> !apeiron$outputPreview.isEmpty()));
    }
}
