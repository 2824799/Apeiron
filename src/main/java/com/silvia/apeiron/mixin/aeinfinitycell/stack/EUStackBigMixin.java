package com.silvia.apeiron.mixin.aeinfinitycell.stack;

import java.io.IOException;
import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStack;
import com.silvia.apeiron.ae.stack.BigAEStackPackets;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.common.integration.aeinfinitycell.InfinityStackCounts;
import com.silvia.apeiron.math.AdaptiveInteger;

import appeng.api.storage.data.IAEStack;
import io.netty.buffer.ByteBuf;

/** Optional AppEU stack extension; the surrounding power machinery keeps its native transfer limits. */
@Pseudo
@Mixin(targets = "cn.dancingsnow.appeu.storage.EUStack", remap = false)
public abstract class EUStackBigMixin implements BigAEStack {

    @Shadow
    private long amount;
    @Unique
    private AdaptiveInteger apeiron$amountBig;

    @Override
    public BigInteger getStackSizeBig() {
        return apeiron$amountBig == null ? BigInteger.valueOf(amount) : apeiron$amountBig.toBigInteger();
    }

    @Override
    public IAEStack<?> setStackSizeBig(final BigInteger value) {
        apeiron$amountBig = AdaptiveInteger.fitsLong(value) ? null : new AdaptiveInteger(value);
        amount = BigAEStackValues.saturatedLong(value);
        return (IAEStack<?>) (Object) this;
    }

    @Override
    public void incStackSizeBig(final BigInteger value) {
        setStackSizeBig(getStackSizeBig().add(value));
    }

    @Override
    public void decStackSizeBig(final BigInteger value) {
        setStackSizeBig(getStackSizeBig().subtract(value));
    }

    @Override
    public boolean isStackSizeBig() {
        return apeiron$amountBig != null;
    }

    @Inject(method = "setStackSize(J)Lcn/dancingsnow/appeu/storage/EUStack;", at = @At("HEAD"), require = 1)
    private void apeiron$legacySet(final long value, final CallbackInfoReturnable<IAEStack<?>> cir) {
        apeiron$amountBig = null;
    }

    @Inject(method = "reset()Lcn/dancingsnow/appeu/storage/EUStack;", at = @At("HEAD"), require = 1)
    private void apeiron$reset(final CallbackInfoReturnable<IAEStack<?>> cir) {
        apeiron$amountBig = null;
    }

    @Inject(method = "incStackSize", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$increase(final long delta, final CallbackInfo ci) {
        if (apeiron$amountBig != null || ((amount ^ (amount + delta)) & (delta ^ (amount + delta))) < 0) {
            incStackSizeBig(BigInteger.valueOf(delta));
            ci.cancel();
        }
    }

    @Inject(method = "decStackSize", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$decrease(final long delta, final CallbackInfo ci) {
        if (apeiron$amountBig != null || ((amount ^ delta) & (amount ^ (amount - delta))) < 0) {
            decStackSizeBig(BigInteger.valueOf(delta));
            ci.cancel();
        }
    }

    @Inject(method = "copy()Lcn/dancingsnow/appeu/storage/EUStack;", at = @At("RETURN"), require = 1)
    private void apeiron$copy(final CallbackInfoReturnable<IAEStack<?>> cir) {
        if (isStackSizeBig()) BigAEStackValues.set(cir.getReturnValue(), getStackSizeBig());
    }

    @Inject(method = "add(Lcn/dancingsnow/appeu/storage/EUStack;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$add(@org.spongepowered.asm.mixin.injection.Coerce final IAEStack<?> other,
        final CallbackInfo ci) {
        if (other != null) BigAEStackValues.addStorage((IAEStack<?>) (Object) this, other);
        ci.cancel();
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"), require = 1)
    private void apeiron$save(final NBTTagCompound tag, final CallbackInfo ci) {
        InfinityStackCounts.write((IAEStack<?>) (Object) this, tag, "amount");
    }

    @Inject(method = "fromNBT", at = @At("RETURN"), require = 1)
    private static void apeiron$load(final NBTTagCompound tag, final CallbackInfoReturnable<IAEStack<?>> cir) {
        InfinityStackCounts.read(cir.getReturnValue(), tag, "amount");
    }

    @Inject(method = "writeToPacket", at = @At("TAIL"), require = 1)
    private void apeiron$writePacket(final ByteBuf data, final CallbackInfo ci) {
        BigAEStackPackets.write(data, (IAEStack<?>) (Object) this);
    }

    @Inject(method = "fromPacket", at = @At("RETURN"), require = 1)
    private static void apeiron$readPacket(final ByteBuf data, final CallbackInfoReturnable<IAEStack<?>> cir)
        throws IOException {
        if (cir.getReturnValue() != null) BigAEStackPackets.read(data, cir.getReturnValue());
    }

}
