package com.silvia.apeiron.mixin.ae.crafting.gui;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.silvia.apeiron.ae.crafting.core.UnlimitedCraftingSelection;

import appeng.client.gui.implementations.GuiCraftConfirm;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.util.Platform;

@Mixin(value = GuiCraftConfirm.class, remap = false)
public abstract class UnlimitedCraftingSelectionGuiMixin {

    @Shadow
    @Final
    private ContainerCraftConfirm ccc;

    @Redirect(
        method = "drawFG",
        at = @At(value = "INVOKE", target = "Lappeng/util/Platform;formatByteDouble(D)Ljava/lang/String;", ordinal = 1))
    private String apeiron$storage(double bytes) {
        return ((UnlimitedCraftingSelection) ccc).isSelectedStorageUnlimited() ? "\u221e"
            : Platform.formatByteDouble(bytes);
    }

    @Redirect(
        method = "drawFG",
        at = @At(value = "INVOKE", target = "Ljava/text/NumberFormat;format(J)Ljava/lang/String;"))
    private String apeiron$parallel(java.text.NumberFormat format, long cores) {
        return ((UnlimitedCraftingSelection) ccc).isSelectedParallelUnlimited() ? "\u221e" : format.format(cores);
    }
}
