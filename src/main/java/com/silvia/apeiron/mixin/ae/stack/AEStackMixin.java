package com.silvia.apeiron.mixin.ae.stack;

import java.io.IOException;
import java.math.BigInteger;

import net.minecraft.client.gui.FontRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEItemStack;
import com.silvia.apeiron.ae.stack.BigAEItemStacks;
import com.silvia.apeiron.ae.stack.BigAERequestableStack;
import com.silvia.apeiron.ae.stack.BigAEStack;
import com.silvia.apeiron.ae.stack.BigAEStackPackets;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigNumberFormatter;

import appeng.api.config.TerminalFontSize;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.render.StackSizeRenderer;
import appeng.util.item.AEStack;
import io.netty.buffer.ByteBuf;

/** Render exact item counts in AE terminal overlays. */
@Mixin(value = AEStack.class, remap = false)
public abstract class AEStackMixin
    implements BigAEStack, BigAERequestableStack, com.silvia.apeiron.ae.stack.InfiniteAEStack {

    @Unique
    private boolean apeiron$infinite;

    @Override
    public boolean isInfinite() {
        return apeiron$infinite;
    }

    @Override
    public void setInfinite(boolean value) {
        ((IAEStack<?>) (Object) this).setStackSize(0);
        apeiron$infinite = value;
    }

    @Inject(method = "isMeaningful", at = @At("HEAD"), cancellable = true)
    private void apeiron$meaningful(CallbackInfoReturnable<Boolean> cir) {
        if (apeiron$infinite) cir.setReturnValue(true);
    }

    @Shadow
    private long stackSize;

    @Shadow
    private long countRequestable;

    @Shadow
    private long countRequestableCrafts;

    @Unique
    private AdaptiveInteger apeiron$stackSizeBig;

    @Unique
    private AdaptiveInteger apeiron$requestableBig;

    @Unique
    private AdaptiveInteger apeiron$requestableCraftsBig;

    @Override
    public BigInteger getStackSizeBig() {
        if (apeiron$infinite) return BigInteger.valueOf(Long.MAX_VALUE);
        return this.apeiron$stackSizeBig == null ? BigInteger.valueOf(this.stackSize)
            : this.apeiron$stackSizeBig.toBigInteger();
    }

    @Override
    public IAEStack<?> setStackSizeBig(final BigInteger value) {
        apeiron$infinite = false;
        if (AdaptiveInteger.fitsLong(value)) {
            this.apeiron$stackSizeBig = null;
            this.stackSize = value.longValue();
        } else {
            this.apeiron$stackSizeBig = new AdaptiveInteger(value);
            this.stackSize = this.apeiron$stackSizeBig.longValueSaturated();
        }
        return (IAEStack<?>) (Object) this;
    }

    @Override
    public void incStackSizeBig(final BigInteger amount) {
        if (apeiron$infinite) return;
        this.setStackSizeBig(
            this.getStackSizeBig()
                .add(amount));
    }

    @Override
    public void decStackSizeBig(final BigInteger amount) {
        if (apeiron$infinite) return;
        this.setStackSizeBig(
            this.getStackSizeBig()
                .subtract(amount));
    }

    @Override
    public boolean isStackSizeBig() {
        return this.apeiron$stackSizeBig != null;
    }

    @Inject(method = "getStackSize", at = @At("RETURN"), cancellable = true)
    private void apeiron$getSaturatedStackSize(final CallbackInfoReturnable<Long> cir) {
        if (apeiron$infinite) cir.setReturnValue(Long.MAX_VALUE);
        else if (this.apeiron$stackSizeBig != null) cir.setReturnValue(this.apeiron$stackSizeBig.longValueSaturated());
    }

    @Inject(method = "setStackSize", at = @At("HEAD"), cancellable = true)
    private void apeiron$setLegacyStackSize(final long value, final CallbackInfoReturnable<IAEStack<?>> cir) {
        apeiron$infinite = false;
        this.apeiron$stackSizeBig = null;
        this.stackSize = value;
        cir.setReturnValue((IAEStack<?>) (Object) this);
    }

    @Inject(method = "incStackSize", at = @At("HEAD"), cancellable = true)
    private void apeiron$incLegacyStackSize(final long amount, final CallbackInfo ci) {
        if (apeiron$infinite) {
            ci.cancel();
            return;
        }
        if (this.apeiron$stackSizeBig != null) {
            this.apeiron$stackSizeBig.add(amount);
            this.stackSize = this.apeiron$stackSizeBig.longValueSaturated();
            ci.cancel();
            return;
        }
        final long before = this.stackSize;
        final long after = before + amount;
        if (((before ^ after) & (amount ^ after)) < 0) {
            this.apeiron$stackSizeBig = new AdaptiveInteger(before);
            this.apeiron$stackSizeBig.add(amount);
            this.stackSize = this.apeiron$stackSizeBig.longValueSaturated();
            ci.cancel();
        }
    }

    @Inject(method = "decStackSize", at = @At("HEAD"), cancellable = true)
    private void apeiron$decLegacyStackSize(final long amount, final CallbackInfo ci) {
        if (apeiron$infinite) {
            ci.cancel();
            return;
        }
        if (this.apeiron$stackSizeBig != null) {
            this.apeiron$stackSizeBig.subtract(amount);
            this.stackSize = this.apeiron$stackSizeBig.longValueSaturated();
            ci.cancel();
            return;
        }
        final long before = this.stackSize;
        final long after = before - amount;
        if (((before ^ amount) & (before ^ after)) < 0) {
            this.apeiron$stackSizeBig = new AdaptiveInteger(before);
            this.apeiron$stackSizeBig.subtract(amount);
            this.stackSize = this.apeiron$stackSizeBig.longValueSaturated();
            ci.cancel();
        }
    }

    @Inject(method = "reset", at = @At("HEAD"))
    private void apeiron$resetExactStackSize(final CallbackInfoReturnable<IAEStack<?>> cir) {
        apeiron$infinite = false;
        this.apeiron$stackSizeBig = null;
        this.apeiron$requestableBig = null;
        this.apeiron$requestableCraftsBig = null;
    }

    @Override
    public BigInteger getCountRequestableBig() {
        return this.apeiron$requestableBig == null ? BigInteger.valueOf(this.countRequestable)
            : this.apeiron$requestableBig.toBigInteger();
    }

    @Override
    public IAEStack<?> setCountRequestableBig(final BigInteger value) {
        if (AdaptiveInteger.fitsLong(value)) {
            this.apeiron$requestableBig = null;
            this.countRequestable = value.longValue();
        } else {
            this.apeiron$requestableBig = new AdaptiveInteger(value);
            this.countRequestable = this.apeiron$requestableBig.longValueSaturated();
        }
        return (IAEStack<?>) (Object) this;
    }

    @Override
    public void incCountRequestableBig(final BigInteger amount) {
        this.setCountRequestableBig(
            this.getCountRequestableBig()
                .add(amount));
    }

    @Override
    public void decCountRequestableBig(final BigInteger amount) {
        this.setCountRequestableBig(
            this.getCountRequestableBig()
                .subtract(amount));
    }

    @Override
    public boolean isCountRequestableBig() {
        return this.apeiron$requestableBig != null;
    }

    @Override
    public BigInteger getCountRequestableCraftsBig() {
        return this.apeiron$requestableCraftsBig == null ? BigInteger.valueOf(this.countRequestableCrafts)
            : this.apeiron$requestableCraftsBig.toBigInteger();
    }

    @Override
    public IAEStack<?> setCountRequestableCraftsBig(final BigInteger value) {
        if (AdaptiveInteger.fitsLong(value)) {
            this.apeiron$requestableCraftsBig = null;
            this.countRequestableCrafts = value.longValue();
        } else {
            this.apeiron$requestableCraftsBig = new AdaptiveInteger(value);
            this.countRequestableCrafts = this.apeiron$requestableCraftsBig.longValueSaturated();
        }
        return (IAEStack<?>) (Object) this;
    }

    @Override
    public void incCountRequestableCraftsBig(final BigInteger amount) {
        this.setCountRequestableCraftsBig(
            this.getCountRequestableCraftsBig()
                .add(amount));
    }

    @Override
    public boolean isCountRequestableCraftsBig() {
        return this.apeiron$requestableCraftsBig != null;
    }

    @Inject(method = "getCountRequestable", at = @At("RETURN"), cancellable = true)
    private void apeiron$getSaturatedRequestable(final CallbackInfoReturnable<Long> cir) {
        if (this.apeiron$requestableBig != null) cir.setReturnValue(this.apeiron$requestableBig.longValueSaturated());
    }

    @Inject(method = "setCountRequestable", at = @At("HEAD"), cancellable = true)
    private void apeiron$setLegacyRequestable(final long value, final CallbackInfoReturnable<IAEStack<?>> cir) {
        this.apeiron$requestableBig = null;
        this.countRequestable = value;
        cir.setReturnValue((IAEStack<?>) (Object) this);
    }

    @Inject(method = "incCountRequestable", at = @At("HEAD"), cancellable = true)
    private void apeiron$incLegacyRequestable(final long amount, final CallbackInfo ci) {
        if (this.apeiron$requestableBig != null) {
            this.apeiron$requestableBig.add(amount);
            this.countRequestable = this.apeiron$requestableBig.longValueSaturated();
            ci.cancel();
            return;
        }
        final long before = this.countRequestable;
        final long after = before + amount;
        if (((before ^ after) & (amount ^ after)) < 0) {
            this.apeiron$requestableBig = new AdaptiveInteger(before);
            this.apeiron$requestableBig.add(amount);
            this.countRequestable = this.apeiron$requestableBig.longValueSaturated();
            ci.cancel();
        }
    }

    @Inject(method = "decCountRequestable", at = @At("HEAD"), cancellable = true)
    private void apeiron$decLegacyRequestable(final long amount, final CallbackInfo ci) {
        if (this.apeiron$requestableBig != null) {
            this.apeiron$requestableBig.subtract(amount);
            this.countRequestable = this.apeiron$requestableBig.longValueSaturated();
            ci.cancel();
            return;
        }
        final long before = this.countRequestable;
        final long after = before - amount;
        if (((before ^ amount) & (before ^ after)) < 0) {
            this.apeiron$requestableBig = new AdaptiveInteger(before);
            this.apeiron$requestableBig.subtract(amount);
            this.countRequestable = this.apeiron$requestableBig.longValueSaturated();
            ci.cancel();
        }
    }

    @Inject(method = "getCountRequestableCrafts", at = @At("RETURN"), cancellable = true)
    private void apeiron$getSaturatedRequestableCrafts(final CallbackInfoReturnable<Long> cir) {
        if (this.apeiron$requestableCraftsBig != null) {
            cir.setReturnValue(this.apeiron$requestableCraftsBig.longValueSaturated());
        }
    }

    @Inject(method = "setCountRequestableCrafts", at = @At("HEAD"), cancellable = true)
    private void apeiron$setLegacyRequestableCrafts(final long value, final CallbackInfoReturnable<IAEStack<?>> cir) {
        this.apeiron$requestableCraftsBig = null;
        this.countRequestableCrafts = value;
        cir.setReturnValue((IAEStack<?>) (Object) this);
    }

    @Inject(method = "incCountRequestableCrafts", at = @At("HEAD"), cancellable = true)
    private void apeiron$incLegacyRequestableCrafts(final long amount, final CallbackInfo ci) {
        if (this.apeiron$requestableCraftsBig != null) {
            this.apeiron$requestableCraftsBig.add(amount);
            this.countRequestableCrafts = this.apeiron$requestableCraftsBig.longValueSaturated();
            ci.cancel();
            return;
        }
        final long before = this.countRequestableCrafts;
        final long after = before + amount;
        if (((before ^ after) & (amount ^ after)) < 0) {
            this.apeiron$requestableCraftsBig = new AdaptiveInteger(before);
            this.apeiron$requestableCraftsBig.add(amount);
            this.countRequestableCrafts = this.apeiron$requestableCraftsBig.longValueSaturated();
            ci.cancel();
        }
    }

    @Inject(method = "writeToPacket", at = @At("TAIL"))
    private void apeiron$writeExactPacket(ByteBuf out, CallbackInfo ci) throws IOException {
        if (this instanceof BigAEItemStack) {
            BigAEItemStacks.writePacketExtension(out, (IAEItemStack) (Object) this);
        } else if (this instanceof BigAEStack) {
            BigAEStackPackets.write(out, (IAEStack<?>) (Object) this);
        }
    }

    @Inject(method = "drawOverlayInGui", at = @At("HEAD"), cancellable = true)
    private void apeiron$infinityOverlay(net.minecraft.client.Minecraft mc, int x, int y, boolean amount,
        boolean always, boolean craftText, boolean craftIcon, CallbackInfo ci) {
        if (!apeiron$infinite) return;
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
