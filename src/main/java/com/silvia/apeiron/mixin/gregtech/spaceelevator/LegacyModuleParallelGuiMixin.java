package com.silvia.apeiron.mixin.gregtech.spaceelevator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.silvia.apeiron.common.machine.spaceelevator.ModuleParallelParameter;

import gregtech.common.gui.modularui.multiblock.base.TTMultiblockBaseGui;
import tectech.thing.metaTileEntity.multi.base.parameter.Parameter;

/** Routes the legacy int-only factory to the same exact setting used by modern module panels. */
@Mixin(value = TTMultiblockBaseGui.class, remap = false)
public abstract class LegacyModuleParallelGuiMixin {

    @Inject(method = "createInputWidget", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$exactParallel(ModularPanel panel, PanelSyncManager syncManager, Parameter<?, ?> parameter,
        CallbackInfoReturnable<IWidget> cir) {
        if (parameter instanceof ModuleParallelParameter)
            cir.setReturnValue(((ModuleParallelParameter) parameter).createExactEditor());
    }
}
