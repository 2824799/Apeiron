package com.silvia.apeiron.mixin.aeinfinitycell.nei;

import java.math.BigInteger;

import net.minecraft.client.gui.FontRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.math.BigNumberFormatter;

import appeng.api.config.TerminalFontSize;
import appeng.client.render.StackSizeRenderer;

/** The NEI preview already has exact hover text; only its compact overlay needs replacement. */
@Pseudo
@Mixin(targets = "cn.dancingsnow.aeinfinitycell.nei.InfinityCellViewHandler", remap = false)
public abstract class InfinityPreviewBigMixin {

    @Unique
    private static final ThreadLocal<BigInteger> apeiron$previewCount = new ThreadLocal<>();

    @Redirect(
        method = "drawForeground",
        at = @At(
            value = "INVOKE",
            target = "Lcn/dancingsnow/aeinfinitycell/nei/InfinityCellViewHandler$ViewItemStack;access$500(Lcn/dancingsnow/aeinfinitycell/nei/InfinityCellViewHandler$ViewItemStack;)J"),
        require = 1)
    private long apeiron$capture(@Coerce final Object view) {
        final BigInteger count = ((InfinityPreviewCountAccessor) view).apeiron$getAmount()
            .toBigInteger();
        apeiron$previewCount.set(count);
        return BigAEStackValues.saturatedLong(count);
    }

    @Redirect(
        method = "drawForeground",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/client/render/StackSizeRenderer;drawStackSize(IIJLnet/minecraft/client/gui/FontRenderer;Lappeng/api/config/TerminalFontSize;)V"),
        require = 1)
    private void apeiron$draw(final int x, final int y, final long legacy, final FontRenderer font,
        final TerminalFontSize size) {
        final BigInteger count = apeiron$previewCount.get();
        apeiron$previewCount.remove();
        if (count != null && !BigAEStackValues.fitsLong(count))
            StackSizeRenderer.drawStackSize(x, y, BigNumberFormatter.formatCompact(count), font, size);
        else StackSizeRenderer.drawStackSize(x, y, legacy, font, size);
    }

    @Inject(method = "drawForeground", at = @At("RETURN"), require = 1)
    private void apeiron$clear(final int recipe, final CallbackInfo ci) {
        apeiron$previewCount.remove();
    }
}
