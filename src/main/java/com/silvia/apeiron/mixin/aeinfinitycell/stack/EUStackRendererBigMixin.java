package com.silvia.apeiron.mixin.aeinfinitycell.stack;

import net.minecraft.client.gui.FontRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.silvia.apeiron.ae.stack.BigAEStack;
import com.silvia.apeiron.math.BigNumberFormatter;

import appeng.api.config.TerminalFontSize;
import appeng.client.render.StackSizeRenderer;

/** Client-only compact EU overlay; the count and codec extensions also work on dedicated servers. */
@Pseudo
@Mixin(targets = "cn.dancingsnow.appeu.storage.EUStack", remap = false)
public abstract class EUStackRendererBigMixin {

    @Redirect(
        method = "drawOverlayInGui",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/client/render/StackSizeRenderer;drawStackSize(IIJLnet/minecraft/client/gui/FontRenderer;Lappeng/api/config/TerminalFontSize;)V"),
        require = 1)
    private void apeiron$draw(final int x, final int y, final long count, final FontRenderer font,
        final TerminalFontSize size) {
        final BigAEStack stack = (BigAEStack) (Object) this;
        if (stack.isStackSizeBig()) StackSizeRenderer
            .drawStackSize(x, y, BigNumberFormatter.formatCompact(stack.getStackSizeBig()), font, size);
        else StackSizeRenderer.drawStackSize(x, y, count, font, size);
    }
}
