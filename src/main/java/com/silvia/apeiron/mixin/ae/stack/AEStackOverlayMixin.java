package com.silvia.apeiron.mixin.ae.stack;

import net.minecraft.client.gui.FontRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.stack.BigAEStack;
import com.silvia.apeiron.math.BigNumberFormatter;

import appeng.api.config.TerminalFontSize;
import appeng.client.render.StackSizeRenderer;
import appeng.util.item.AEStack;

/** Client-only stack rendering; common stack state also loads on dedicated servers. */
@Mixin(value = AEStack.class, remap = false)
public abstract class AEStackOverlayMixin {

    @Inject(method = "drawOverlayInGui", at = @At("HEAD"), cancellable = true)
    private void apeiron$infinityOverlay(net.minecraft.client.Minecraft mc, int x, int y, boolean amount,
        boolean always, boolean craftText, boolean craftIcon, CallbackInfo ci) {
        if (!((com.silvia.apeiron.ae.stack.InfiniteAEStack) this).isInfinite()) return;
        if (amount) {
            org.lwjgl.opengl.GL11.glPushMatrix();
            org.lwjgl.opengl.GL11.glTranslatef(0, 0, 200);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_LIGHTING);
            StackSizeRenderer
                .drawStackSize(x, y, "∞", mc.fontRenderer, appeng.core.AEConfig.instance.getTerminalFontSize());
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_LIGHTING);
            org.lwjgl.opengl.GL11.glPopMatrix();
        }
        ci.cancel();
    }

    @Redirect(
        method = "drawOverlayInGui",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/client/render/StackSizeRenderer;drawStackSize(IIJLnet/minecraft/client/gui/FontRenderer;Lappeng/api/config/TerminalFontSize;)V"))
    private void apeiron$drawExactStackSize(int offsetX, int offsetY, long stackSize, FontRenderer font,
        TerminalFontSize fontSize) {
        Object target = this;
        if (target instanceof BigAEStack exact && exact.isStackSizeBig()) {
            StackSizeRenderer.drawStackSize(
                offsetX,
                offsetY,
                BigNumberFormatter.formatCompact(exact.getStackSizeBig()),
                font,
                fontSize);
        } else {
            StackSizeRenderer.drawStackSize(offsetX, offsetY, stackSize, font, fontSize);
        }
    }
}
