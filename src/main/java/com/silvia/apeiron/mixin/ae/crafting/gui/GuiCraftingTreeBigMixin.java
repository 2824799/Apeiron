package com.silvia.apeiron.mixin.ae.crafting.gui;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.terminal.BigGuiNumberCapture;

import appeng.api.storage.data.IAEStack;
import appeng.client.gui.widgets.GuiCraftingTree;
import appeng.crafting.v2.CraftingRequest;
import appeng.util.ReadableNumberConverter;

/** Keeps crafting-tree stack labels exact after AE converts them to legacy longs. */
@Mixin(value = GuiCraftingTree.class, remap = false)
public abstract class GuiCraftingTreeBigMixin {

    @Inject(method = "getDisplayItemForRequest", at = @At("RETURN"), cancellable = true, require = 1)
    private void apeiron$preserveRequestSize(CraftingRequest request, CallbackInfoReturnable<IAEStack<?>> cir) {
        cir.setReturnValue(BigAEStackValues.copyWithSize(cir.getReturnValue(), BigAEStackValues.get(request.stack)));
    }

    @Redirect(
        method = "drawStack",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IAEStack;getStackSize()J"))
    private long apeiron$captureStack(final IAEStack<?> stack) {
        return BigGuiNumberCapture.captureStack(stack);
    }

    @Redirect(
        method = "drawSmallStackCount(IIJI)V",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/util/ReadableNumberConverter;toWideReadableForm(J)Ljava/lang/String;"))
    private String apeiron$formatWide(final ReadableNumberConverter converter, final long value) {
        return BigGuiNumberCapture.formatWideStack(value);
    }
}
