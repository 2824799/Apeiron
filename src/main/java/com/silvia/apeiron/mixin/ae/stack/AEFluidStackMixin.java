package com.silvia.apeiron.mixin.ae.stack;

import java.io.IOException;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStack;
import com.silvia.apeiron.ae.stack.BigAERequestableStack;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.storage.data.IAEFluidStack;
import appeng.util.item.AEFluidStack;
import io.netty.buffer.ByteBuf;

/** Preserves exact generic stack counts for AE fluids. */
@Mixin(value = AEFluidStack.class, remap = false)
public abstract class AEFluidStackMixin {

    @Inject(method = "add(Lappeng/api/storage/data/IAEFluidStack;)V", at = @At("HEAD"), cancellable = true)
    private void apeiron$addExact(final IAEFluidStack other, final CallbackInfo ci) {
        if (other == null) { ci.cancel(); return; }
        final IAEFluidStack self = (IAEFluidStack) (Object) this;
        com.silvia.apeiron.ae.stack.BigAEStackValues.addStorage(self, other);
        com.silvia.apeiron.ae.stack.BigAEStackValues.addRequestable(self, other);
        self.setCraftable(self.isCraftable() || other.isCraftable());
        self.setUsedPercent(self.getUsedPercent() + other.getUsedPercent());
        ci.cancel();
    }

    @Inject(method = "<init>(Lappeng/util/item/AEFluidStack;)V", at = @At("TAIL"))
    private void apeiron$copyExact(final AEFluidStack source, final CallbackInfo ci) {
        if ((Object) source instanceof BigAEStack && ((BigAEStack) (Object) source).isStackSizeBig()) {
            ((BigAEStack) (Object) this).setStackSizeBig(((BigAEStack) (Object) source).getStackSizeBig());
        }
        if ((Object) source instanceof BigAERequestableStack) {
            final BigAERequestableStack exact = (BigAERequestableStack) (Object) source;
            final BigAERequestableStack target = (BigAERequestableStack) (Object) this;
            if (exact.isCountRequestableBig()) target.setCountRequestableBig(exact.getCountRequestableBig());
            if (exact.isCountRequestableCraftsBig()) {
                target.setCountRequestableCraftsBig(exact.getCountRequestableCraftsBig());
            }
        }
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void apeiron$writeExactNBT(final NBTTagCompound tag, final CallbackInfo ci) {
        final BigAEStack exact = (BigAEStack) (Object) this;
        if (exact.isStackSizeBig()) {
            BigValueCodec.writeNBT(tag, "Cnt", "ApeironCnt", new AdaptiveInteger(exact.getStackSizeBig()));
        } else {
            tag.removeTag("ApeironCnt");
        }
        final BigAERequestableStack requestable = (BigAERequestableStack) (Object) this;
        if (requestable.isCountRequestableBig()) {
            BigValueCodec.writeNBT(
                    tag,
                    "Req",
                    "ApeironReq",
                    new AdaptiveInteger(requestable.getCountRequestableBig()));
        } else {
            tag.removeTag("ApeironReq");
        }
        if (requestable.isCountRequestableCraftsBig()) {
            BigValueCodec.writeNBT(
                    tag,
                    "ReqMade",
                    "ApeironReqMade",
                    new AdaptiveInteger(requestable.getCountRequestableCraftsBig()));
        } else {
            tag.removeTag("ApeironReqMade");
        }
    }

    @Inject(method = "loadFluidStackFromNBT", at = @At("RETURN"))
        private static void apeiron$readExactNBT(final NBTTagCompound tag,
        final CallbackInfoReturnable<IAEFluidStack> cir) {
        final IAEFluidStack stack = cir.getReturnValue();
        if (stack == null) return;
        final BigAEStack exact = (BigAEStack) stack;
        if (tag.hasKey("ApeironCnt")) {
            exact.setStackSizeBig(BigValueCodec.readNBT(tag, "Cnt", "ApeironCnt").toBigInteger());
        }
        final BigAERequestableStack requestable = (BigAERequestableStack) stack;
        if (tag.hasKey("ApeironReq")) {
            requestable.setCountRequestableBig(BigValueCodec.readNBT(tag, "Req", "ApeironReq").toBigInteger());
        }
        if (tag.hasKey("ApeironReqMade")) {
            requestable.setCountRequestableCraftsBig(
                    BigValueCodec.readNBT(tag, "ReqMade", "ApeironReqMade").toBigInteger());
        }
    }

    @Inject(method = "loadFluidStackFromPacket", at = @At("RETURN"))
    private static void apeiron$readExactPacket(final ByteBuf data,
        final CallbackInfoReturnable<IAEFluidStack> cir) throws IOException {
        final IAEFluidStack stack = cir.getReturnValue();
        if (stack != null) com.silvia.apeiron.ae.stack.BigAEStackPackets.read(data, stack);
    }
}
