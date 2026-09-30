package com.silvia.apeiron.mixin.ae.stack;

import java.io.IOException;
import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEItemStack;
import com.silvia.apeiron.ae.stack.BigAEItemStacks;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEStack;
import io.netty.buffer.ByteBuf;

/** Adds exact counts to AE2's own final AEItemStack instead of loading a duplicate class. */
@Mixin(value = AEItemStack.class, remap = false)
public abstract class AEItemStackMixin extends AEStack<IAEItemStack> implements BigAEItemStack {

    // Null means the inherited long field is exact. No extra counter object is allocated on the common path.
    @Unique
    private AdaptiveInteger apeiron$stackSizeBig;

    @Unique
    private AdaptiveInteger apeiron$requestableBig;

    @Unique
    private AdaptiveInteger apeiron$requestableCraftsBig;

    @Override
    public IAEItemStack setStackSize(long value) {
        apeiron$stackSizeBig = null;
        return super.setStackSize(value);
    }

    @Override
    public void incStackSize(long amount) {
        if (apeiron$stackSizeBig != null) {
            apeiron$stackSizeBig.add(amount);
            apeiron$syncStackSize();
            return;
        }
        long before = super.getStackSize();
        long after = before + amount;
        if (((before ^ after) & (amount ^ after)) < 0) {
            apeiron$stackSizeBig = new AdaptiveInteger(before);
            apeiron$stackSizeBig.add(amount);
            apeiron$syncStackSize();
        } else {
            super.setStackSize(after);
        }
    }

    @Override
    public void decStackSize(long amount) {
        if (apeiron$stackSizeBig != null) {
            apeiron$stackSizeBig.subtract(amount);
            apeiron$syncStackSize();
            return;
        }
        long before = super.getStackSize();
        long after = before - amount;
        if (((before ^ amount) & (before ^ after)) < 0) {
            apeiron$stackSizeBig = new AdaptiveInteger(before);
            apeiron$stackSizeBig.subtract(amount);
            apeiron$syncStackSize();
        } else {
            super.setStackSize(after);
        }
    }

    @Override
    public BigInteger getStackSizeBig() {
        return apeiron$stackSizeBig == null ? BigInteger.valueOf(super.getStackSize())
            : apeiron$stackSizeBig.toBigInteger();
    }

    @Override
    public IAEItemStack setStackSizeBig(BigInteger value) {
        if (AdaptiveInteger.fitsLong(value)) {
            return setStackSize(value.longValue());
        }
        if (apeiron$stackSizeBig == null) {
            apeiron$stackSizeBig = new AdaptiveInteger(value);
        } else {
            apeiron$stackSizeBig.set(value);
        }
        super.setStackSize(apeiron$stackSizeBig.longValueSaturated());
        return (IAEItemStack) (Object) this;
    }

    @Override
    public void incStackSizeBig(BigInteger amount) {
        if (AdaptiveInteger.fitsLong(amount)) {
            incStackSize(amount.longValue());
        } else {
            setStackSizeBig(getStackSizeBig().add(amount));
        }
    }

    @Override
    public void decStackSizeBig(BigInteger amount) {
        if (AdaptiveInteger.fitsLong(amount)) {
            decStackSize(amount.longValue());
        } else {
            setStackSizeBig(getStackSizeBig().subtract(amount));
        }
    }

    @Override
    public boolean isStackSizeBig() {
        return apeiron$stackSizeBig != null;
    }

    @Unique
    private void apeiron$syncStackSize() {
        if (apeiron$stackSizeBig.isBig()) {
            super.setStackSize(apeiron$stackSizeBig.longValueSaturated());
        } else {
            super.setStackSize(apeiron$stackSizeBig.longValueExact());
            apeiron$stackSizeBig = null;
        }
    }

    @Override
    public IAEItemStack setCountRequestable(long value) {
        apeiron$requestableBig = null;
        return super.setCountRequestable(value);
    }

    @Override
    public void incCountRequestable(long amount) {
        if (apeiron$requestableBig != null) {
            apeiron$requestableBig.add(amount);
            apeiron$syncRequestable();
            return;
        }
        long before = super.getCountRequestable();
        long after = before + amount;
        if (((before ^ after) & (amount ^ after)) < 0) {
            apeiron$requestableBig = new AdaptiveInteger(before);
            apeiron$requestableBig.add(amount);
            apeiron$syncRequestable();
        } else {
            super.setCountRequestable(after);
        }
    }

    @Override
    public void decCountRequestable(long amount) {
        if (apeiron$requestableBig != null) {
            apeiron$requestableBig.subtract(amount);
            apeiron$syncRequestable();
            return;
        }
        long before = super.getCountRequestable();
        long after = before - amount;
        if (((before ^ amount) & (before ^ after)) < 0) {
            apeiron$requestableBig = new AdaptiveInteger(before);
            apeiron$requestableBig.subtract(amount);
            apeiron$syncRequestable();
        } else {
            super.setCountRequestable(after);
        }
    }

    @Override
    public BigInteger getCountRequestableBig() {
        return apeiron$requestableBig == null ? BigInteger.valueOf(super.getCountRequestable())
            : apeiron$requestableBig.toBigInteger();
    }

    @Override
    public IAEItemStack setCountRequestableBig(BigInteger value) {
        if (AdaptiveInteger.fitsLong(value)) {
            return setCountRequestable(value.longValue());
        }
        if (apeiron$requestableBig == null) {
            apeiron$requestableBig = new AdaptiveInteger(value);
        } else {
            apeiron$requestableBig.set(value);
        }
        super.setCountRequestable(apeiron$requestableBig.longValueSaturated());
        return (IAEItemStack) (Object) this;
    }

    @Override
    public void incCountRequestableBig(BigInteger amount) {
        if (AdaptiveInteger.fitsLong(amount)) {
            incCountRequestable(amount.longValue());
        } else {
            setCountRequestableBig(getCountRequestableBig().add(amount));
        }
    }

    @Override
    public void decCountRequestableBig(BigInteger amount) {
        if (AdaptiveInteger.fitsLong(amount)) {
            decCountRequestable(amount.longValue());
        } else {
            setCountRequestableBig(getCountRequestableBig().subtract(amount));
        }
    }

    @Override
    public boolean isCountRequestableBig() {
        return apeiron$requestableBig != null;
    }

    @Unique
    private void apeiron$syncRequestable() {
        if (apeiron$requestableBig.isBig()) {
            super.setCountRequestable(apeiron$requestableBig.longValueSaturated());
        } else {
            super.setCountRequestable(apeiron$requestableBig.longValueExact());
            apeiron$requestableBig = null;
        }
    }

    @Override
    public IAEItemStack setCountRequestableCrafts(long value) {
        apeiron$requestableCraftsBig = null;
        return super.setCountRequestableCrafts(value);
    }

    @Override
    public BigInteger getCountRequestableCraftsBig() {
        return apeiron$requestableCraftsBig == null ? BigInteger.valueOf(super.getCountRequestableCrafts())
            : apeiron$requestableCraftsBig.toBigInteger();
    }

    @Override
    public IAEItemStack setCountRequestableCraftsBig(BigInteger value) {
        if (AdaptiveInteger.fitsLong(value)) {
            return setCountRequestableCrafts(value.longValue());
        }
        if (apeiron$requestableCraftsBig == null) {
            apeiron$requestableCraftsBig = new AdaptiveInteger(value);
        } else {
            apeiron$requestableCraftsBig.set(value);
        }
        super.setCountRequestableCrafts(apeiron$requestableCraftsBig.longValueSaturated());
        return (IAEItemStack) (Object) this;
    }

    @Override
    public void incCountRequestableCrafts(long amount) {
        apeiron$addRequestableCrafts(amount);
    }

    @Override
    public void incCountRequestableCraftsBig(BigInteger amount) {
        if (AdaptiveInteger.fitsLong(amount)) {
            apeiron$addRequestableCrafts(amount.longValue());
        } else {
            setCountRequestableCraftsBig(getCountRequestableCraftsBig().add(amount));
        }
    }

    @Override
    public boolean isCountRequestableCraftsBig() {
        return apeiron$requestableCraftsBig != null;
    }

    @Override
    public IAEItemStack reset() {
        IAEItemStack result = super.reset();
        apeiron$stackSizeBig = null;
        apeiron$requestableBig = null;
        apeiron$requestableCraftsBig = null;
        return result;
    }

    @Override
    public void addBig(IAEItemStack other) {
        if (other == null) {
            return;
        }
        if (other instanceof BigAEItemStack) {
            BigAEItemStack exact = (BigAEItemStack) other;
            if (exact.isStackSizeBig()) {
                incStackSizeBig(exact.getStackSizeBig());
            } else {
                incStackSize(other.getStackSize());
            }
            if (exact.isCountRequestableBig()) {
                incCountRequestableBig(exact.getCountRequestableBig());
            } else {
                incCountRequestable(other.getCountRequestable());
            }
            if (exact.isCountRequestableCraftsBig()) {
                incCountRequestableCraftsBig(exact.getCountRequestableCraftsBig());
            } else {
                apeiron$addRequestableCrafts(other.getCountRequestableCrafts());
            }
        } else {
            incStackSize(other.getStackSize());
            incCountRequestable(other.getCountRequestable());
            apeiron$addRequestableCrafts(other.getCountRequestableCrafts());
        }
        setCraftable(isCraftable() || other.isCraftable());
        setUsedPercent(getUsedPercent() + other.getUsedPercent());
    }

    @Unique
    private void apeiron$addRequestableCrafts(long amount) {
        if (apeiron$requestableCraftsBig != null) {
            apeiron$requestableCraftsBig.add(amount);
            setCountRequestableCraftsBig(apeiron$requestableCraftsBig.toBigInteger());
            return;
        }
        long before = super.getCountRequestableCrafts();
        long after = before + amount;
        if (((before ^ after) & (amount ^ after)) < 0) {
            setCountRequestableCraftsBig(
                BigInteger.valueOf(before)
                    .add(BigInteger.valueOf(amount)));
        } else {
            super.setCountRequestableCrafts(after);
        }
    }

    @Inject(method = "add(Lappeng/api/storage/data/IAEItemStack;)V", at = @At("HEAD"), cancellable = true)
    private void apeiron$addExact(IAEItemStack other, CallbackInfo ci) {
        addBig(other);
        ci.cancel();
    }

    @Inject(method = "<init>(Lappeng/util/item/AEItemStack;)V", at = @At("TAIL"))
    private void apeiron$copyExactValues(AEItemStack source, CallbackInfo ci) {
        BigAEItemStack exact = (BigAEItemStack) (Object) source;
        if (exact.isStackSizeBig()) setStackSizeBig(exact.getStackSizeBig());
        if (exact.isCountRequestableBig()) setCountRequestableBig(exact.getCountRequestableBig());
        if (exact.isCountRequestableCraftsBig()) setCountRequestableCraftsBig(exact.getCountRequestableCraftsBig());
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void apeiron$writeExactNBT(NBTTagCompound tag, CallbackInfo ci) {
        if (apeiron$stackSizeBig != null) {
            BigValueCodec.writeNBT(tag, "Cnt", "ApeironCnt", apeiron$stackSizeBig);
        } else {
            tag.removeTag("ApeironCnt");
        }
        if (apeiron$requestableBig != null) {
            BigValueCodec.writeNBT(tag, "Req", "ApeironReq", apeiron$requestableBig);
        } else {
            tag.removeTag("ApeironReq");
        }
        if (apeiron$requestableCraftsBig != null) {
            BigValueCodec.writeNBT(tag, "ReqMade", "ApeironReqMade", apeiron$requestableCraftsBig);
        } else {
            tag.removeTag("ApeironReqMade");
        }
    }

    @Inject(method = "loadItemStackFromNBT", at = @At("RETURN"))
    private static void apeiron$readExactNBT(NBTTagCompound tag, CallbackInfoReturnable<IAEItemStack> cir) {
        IAEItemStack stack = cir.getReturnValue();
        if (stack == null) return;
        BigAEItemStack exact = (BigAEItemStack) stack;
        if (tag.hasKey("ApeironCnt")) {
            exact.setStackSizeBig(
                BigValueCodec.readNBT(tag, "Cnt", "ApeironCnt")
                    .toBigInteger());
        }
        if (tag.hasKey("ApeironReq")) {
            exact.setCountRequestableBig(
                BigValueCodec.readNBT(tag, "Req", "ApeironReq")
                    .toBigInteger());
        }
        if (tag.hasKey("ApeironReqMade")) {
            exact.setCountRequestableCraftsBig(
                BigValueCodec.readNBT(tag, "ReqMade", "ApeironReqMade")
                    .toBigInteger());
        }
    }

    @Inject(method = "loadItemStackFromPacket", at = @At("RETURN"))
    private static void apeiron$readExactPacket(ByteBuf data, CallbackInfoReturnable<IAEItemStack> cir)
        throws IOException {
        IAEItemStack stack = cir.getReturnValue();
        if (stack != null) BigAEItemStacks.readPacketExtension(data, stack);
    }

    @Override
    public void writeToBigPacket(ByteBuf out) throws IOException {
        super.writeToPacket(out);
    }
}
