package com.silvia.apeiron.mixin.ae.crafting.gui;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.terminal.BigGuiNumberCapture;
import com.silvia.apeiron.crafting.BigCraftingTree;

import appeng.api.storage.data.IAEStack;
import appeng.crafting.v2.resolvers.CraftableItemResolver.CraftFromPatternTask;

/** Captures the operation count before the native task node sends it through a long-only drawing method. */
@Mixin(targets = "appeng.client.gui.widgets.GuiCraftingTree$TaskNode", remap = false)
public abstract class GuiCraftingTreeTaskBigMixin {

    @Redirect(
        method = "drawImpl",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IAEStack;getStackSize()J"),
        require = 1)
    private long apeiron$captureResolved(IAEStack<?> stack) {
        return BigGuiNumberCapture.captureStack(stack);
    }

    @Redirect(
        method = "drawImpl",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/crafting/v2/resolvers/CraftableItemResolver$CraftFromPatternTask;getTotalCraftsDone()J"),
        require = 1)
    private long apeiron$captureCrafts(CraftFromPatternTask task) {
        return BigGuiNumberCapture.captureStack(
            BigAEStackValues.copyWithSize(
                task.request.stack,
                task instanceof BigCraftingTree.PatternTask ? ((BigCraftingTree.PatternTask) task).getExactCrafts()
                    : java.math.BigInteger.valueOf(task.getTotalCraftsDone())));
    }
}
