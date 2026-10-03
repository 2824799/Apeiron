package com.silvia.apeiron.mixin.gregtech.energy;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.value.sync.DynamicLinkedSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.silvia.apeiron.client.gui.machine.energy.WirelessRecipeWidgets;
import com.silvia.apeiron.client.gui.sync.ChunkedNbtSyncValue;
import com.silvia.apeiron.common.machine.energy.MachineRecipeDisplay;
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
    private ChunkedNbtSyncValue apeiron$recipeDisplay;

    @Inject(method = "registerSyncValues", at = @At("RETURN"), require = 1)
    private void apeiron$register(PanelSyncManager sync, CallbackInfo ci) {
        apeiron$recipeDisplay = new ChunkedNbtSyncValue(() -> MachineRecipeDisplay.snapshot(multiblock), null);
        sync.syncValue("apeiron_recipe_output_display", apeiron$recipeDisplay);
    }

    // GT's original method installs allowC2S listeners on native output lists. Do not build it, even when hidden.
    @Inject(method = "createRecipeInfoWidget", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$outputs(PanelSyncManager sync, CallbackInfoReturnable<IWidget> cir) {
        final IntSyncValue duration = (IntSyncValue) sync.getSyncHandlerFromMapKey("maxProgressTime:0");
        final DynamicLinkedSyncHandler<ChunkedNbtSyncValue> handler = new DynamicLinkedSyncHandler<>(
            apeiron$recipeDisplay).widgetProvider((manager, value) -> {
                final BigMachineOutputQueue preview = new BigMachineOutputQueue();
                preview.load(value.getValue());
                final List<IAEStack<?>> outputs = preview.snapshotOutputs();
                return WirelessRecipeWidgets.modern(outputs, duration::getIntValue);
            });
        cir.setReturnValue(
            new DynamicSyncedWidget<>().widthRel(0.85f)
                .coverChildrenHeight(0)
                .syncHandler(handler));
    }
}
