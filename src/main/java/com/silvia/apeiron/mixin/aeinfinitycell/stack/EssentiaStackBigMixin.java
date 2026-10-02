package com.silvia.apeiron.mixin.aeinfinitycell.stack;

import java.io.IOException;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackPackets;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.common.integration.aeinfinitycell.InfinityStackCounts;

import io.netty.buffer.ByteBuf;
import thaumicenergistics.common.storage.AEEssentiaStack;

/** Preserves exact source counts through Thaumic Energistics copies, merges and codecs. */
@Pseudo
@Mixin(targets = "thaumicenergistics.common.storage.AEEssentiaStack", remap = false)
public abstract class EssentiaStackBigMixin {

    @Inject(method = "<init>(Lthaumicenergistics/common/storage/AEEssentiaStack;)V", at = @At("TAIL"), require = 1)
    private void apeiron$copy(final AEEssentiaStack source, final CallbackInfo ci) {
        InfinityStackCounts.copy(source, (AEEssentiaStack) (Object) this);
    }

    @Inject(
        method = "add(Lthaumicenergistics/common/storage/AEEssentiaStack;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$add(final AEEssentiaStack other, final CallbackInfo ci) {
        if (other != null) {
            final AEEssentiaStack self = (AEEssentiaStack) (Object) this;
            BigAEStackValues.addStorage(self, other);
            BigAEStackValues.addRequestable(self, other);
            self.setCraftable(self.isCraftable() || other.isCraftable());
        }
        ci.cancel();
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"), require = 1)
    private void apeiron$save(final NBTTagCompound tag, final CallbackInfo ci) {
        InfinityStackCounts.write((AEEssentiaStack) (Object) this, tag, "Cnt");
    }

    @Inject(method = "loadStackFromNBT", at = @At("RETURN"), require = 1)
    private static void apeiron$load(final NBTTagCompound tag, final CallbackInfoReturnable<AEEssentiaStack> cir) {
        InfinityStackCounts.read(cir.getReturnValue(), tag, "Cnt");
    }

    @Inject(method = "loadEssentiaStackFromPacket", at = @At("RETURN"), require = 1)
    private static void apeiron$packet(final ByteBuf data, final CallbackInfoReturnable<AEEssentiaStack> cir)
        throws IOException {
        if (cir.getReturnValue() != null) BigAEStackPackets.read(data, cir.getReturnValue());
    }
}
