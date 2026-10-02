package com.silvia.apeiron.mixin.ae.crafting.core;

import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.crafting.packets.BigCraftNotification;
import com.silvia.apeiron.ae.crafting.packets.BigCraftNotificationValues;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigValueCodec;
import com.silvia.apeiron.math.BigNumberFormatter;

import appeng.me.cluster.implementations.CraftingCPUCluster;

/** Keeps completion notifications exact in chat and persistent unread notifications. */
@Mixin(value = CraftingCPUCluster.CraftNotification.class, remap = false)
public abstract class CraftNotificationBigMixin implements BigCraftNotification {

    @Shadow private long outputsCount;

    @Unique
    private BigInteger apeiron$outputsCount;

    @Inject(method = "<init>(Lappeng/api/storage/data/IAEStack;JJ)V", at = @At("TAIL"))
    private void apeiron$capture(final appeng.api.storage.data.IAEStack<?> finalOutput,
        final long outputsCount, final long elapsedTime, final CallbackInfo ci) {
        this.apeiron$outputsCount = BigCraftNotificationValues.take(outputsCount);
    }

    @Override
    public BigInteger getOutputsCountBig() {
        return this.apeiron$outputsCount == null
            ? BigInteger.valueOf(this.outputsCount) : this.apeiron$outputsCount;
    }

    @Inject(method = "getOutputsCount", at = @At("RETURN"), cancellable = true)
    private void apeiron$saturate(final CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(BigAEStackValues.saturatedLong(this.getOutputsCountBig()));
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void apeiron$read(final NBTTagCompound tag, final CallbackInfo ci) {
        if (tag.hasKey("ApeironOutputsCount")) {
            this.apeiron$outputsCount = BigValueCodec.readNBT(
                tag, "outputsCount", "ApeironOutputsCount").toBigInteger();
        }
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void apeiron$write(final NBTTagCompound tag, final CallbackInfo ci) {
        BigValueCodec.writeNBT(
            tag,
            "outputsCount",
            "ApeironOutputsCount",
            new AdaptiveInteger(this.getOutputsCountBig()));
    }

    @Redirect(
        method = "createMessage",
        at = @At(value = "INVOKE", target = "Ljava/lang/String;valueOf(J)Ljava/lang/String;"))
    private String apeiron$format(final long legacy) {
        return BigNumberFormatter.formatExact(this.getOutputsCountBig());
    }
}
