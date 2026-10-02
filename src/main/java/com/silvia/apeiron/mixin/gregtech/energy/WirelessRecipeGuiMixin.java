package com.silvia.apeiron.mixin.gregtech.energy;

import java.io.IOException;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.serialization.ByteBufAdapters;
import com.cleanroommc.modularui.value.sync.DynamicSyncHandler;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.client.gui.machine.energy.WirelessRecipeWidgets;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;

import appeng.api.storage.data.IAEStack;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

@Mixin(value = MTEMultiBlockBaseGui.class, remap = false)
public abstract class WirelessRecipeGuiMixin {

    @Shadow
    @Final
    protected MTEMultiBlockBase multiblock;

    @Unique
    private GenericSyncValue<NBTTagCompound, ?> apeiron$recipeDisplay;

    @Inject(method = "registerSyncValues", at = @At("RETURN"), require = 1)
    private void apeiron$register(PanelSyncManager sync, CallbackInfo ci) {
        apeiron$recipeDisplay = GenericSyncValue.builder(NBTTagCompound.class)
            .getter(
                () -> ((BigWirelessController) multiblock).getWirelessRecipeState()
                    .writeDisplayNBT())
            .adapter(ByteBufAdapters.NBT)
            .copy(tag -> (NBTTagCompound) tag.copy())
            .build();
        sync.syncValue("apeiron_recipe_output_display", apeiron$recipeDisplay);
    }

    @Inject(method = "createRecipeInfoWidget", at = @At("RETURN"), cancellable = true, require = 1)
    private void apeiron$outputs(PanelSyncManager sync, CallbackInfoReturnable<IWidget> cir) {
        final IntSyncValue duration = (IntSyncValue) sync.getSyncHandlerFromMapKey("maxProgressTime:0");
        final DynamicSyncHandler handler = new DynamicSyncHandler().widgetProvider((manager, packet) -> {
            if (packet == null) return new com.cleanroommc.modularui.widget.EmptyWidget();
            try {
                final NBTTagCompound snapshot = ByteBufAdapters.NBT.deserialize(packet);
                final BigMachineOutputQueue preview = new BigMachineOutputQueue();
                preview.load(snapshot);
                final List<IAEStack<?>> outputs = preview.snapshotOutputs();
                return WirelessRecipeWidgets.modern(outputs, duration::getIntValue);
            } catch (IOException error) {
                throw new IllegalStateException("Invalid wireless recipe display", error);
            }
        });
        apeiron$recipeDisplay.setChangeListener(() -> {
            // Reading the server snapshot also fires this listener on the client.
            // Only the server should publish the dynamic output widget.
            if (sync.isClient()) return;
            handler.notifyUpdate(packet -> {
                try {
                    ByteBufAdapters.NBT.serialize(packet, apeiron$recipeDisplay.getValue());
                } catch (IOException error) {
                    throw new IllegalStateException("Cannot synchronize wireless recipe display", error);
                }
            });
        });
        cir.setReturnValue(
            Flow.column()
                .fullWidth()
                .coverChildren()
                .child(
                    new ParentWidget<>().fullWidth()
                        .coverChildrenHeight()
                        .child(cir.getReturnValue())
                        .setEnabledIf(
                            widget -> !apeiron$recipeDisplay.getValue()
                                .getBoolean("running")))
                .child(
                    new DynamicSyncedWidget<>().widthRel(0.85f)
                        .coverChildrenHeight(0)
                        .syncHandler(handler)
                        .setEnabledIf(
                            widget -> apeiron$recipeDisplay.getValue()
                                .getBoolean("running"))));
    }
}
