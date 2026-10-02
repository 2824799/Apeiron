package com.silvia.apeiron.mixin.ae.crafting.core;

import java.io.IOException;
import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.crafting.core.BigCraftingCpuEntries;
import com.silvia.apeiron.ae.crafting.core.BigCraftingCpuEntry;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.storage.data.IAEStack;
import appeng.container.implementations.CraftingCpuEntry;
import appeng.util.ScheduledReason;
import io.netty.buffer.ByteBuf;

/** Keeps crafting CPU item rows exact through the legacy entry and packet APIs. */
@Mixin(value = CraftingCpuEntry.class, remap = false)
public abstract class CraftingCpuEntryMixin implements BigCraftingCpuEntry {

    private static final int APEIRON_PACKET_MAGIC = 0x41504345;

    @Unique
    private BigInteger apeiron$stored;
    @Unique
    private BigInteger apeiron$active;
    @Unique
    private BigInteger apeiron$pending;

    @Inject(method = "<init>(Lappeng/api/storage/data/IAEStack;JJJLappeng/util/ScheduledReason;)V", at = @At("TAIL"))
    private void apeiron$captureConstructor(final IAEStack<?> stack, final long stored, final long active,
        final long pending, final ScheduledReason reason, final CallbackInfo ci) {
        final BigInteger[] values = BigCraftingCpuEntries.take(stored, active, pending);
        this.apeiron$stored = values[0];
        this.apeiron$active = values[1];
        this.apeiron$pending = values[2];
    }

    @Inject(method = "<init>(Lio/netty/buffer/ByteBuf;)V", at = @At("TAIL"))
    private void apeiron$readExactPacket(final ByteBuf buffer, final CallbackInfo ci) {
        if (!buffer.isReadable() || buffer.readInt() != APEIRON_PACKET_MAGIC) {
            throw new IllegalStateException("Missing Apeiron crafting CPU entry extension");
        }
        try {
            this.apeiron$stored = BigValueCodec.readPacket(buffer);
            this.apeiron$active = BigValueCodec.readPacket(buffer);
            this.apeiron$pending = BigValueCodec.readPacket(buffer);
        } catch (final IOException e) {
            throw new IllegalStateException("Invalid Apeiron crafting CPU entry extension", e);
        }
    }

    @Inject(method = "writeToPacket", at = @At("TAIL"))
    private void apeiron$writeExactPacket(final ByteBuf buffer, final CallbackInfo ci) {
        buffer.writeInt(APEIRON_PACKET_MAGIC);
        BigValueCodec.writePacket(buffer, this.getStoredAmountBig());
        BigValueCodec.writePacket(buffer, this.getActiveAmountBig());
        BigValueCodec.writePacket(buffer, this.getPendingAmountBig());
    }

    @Override
    public BigInteger getStoredAmountBig() {
        return this.apeiron$stored == null ? BigInteger.valueOf(((CraftingCpuEntry) (Object) this).getStoredAmount())
            : this.apeiron$stored;
    }

    @Override
    public BigInteger getActiveAmountBig() {
        return this.apeiron$active == null ? BigInteger.valueOf(((CraftingCpuEntry) (Object) this).getActiveAmount())
            : this.apeiron$active;
    }

    @Override
    public BigInteger getPendingAmountBig() {
        return this.apeiron$pending == null ? BigInteger.valueOf(((CraftingCpuEntry) (Object) this).getPendingAmount())
            : this.apeiron$pending;
    }

    @Override
    public BigInteger getTotalAmountBig() {
        return this.getStoredAmountBig()
            .add(this.getActiveAmountBig())
            .add(this.getPendingAmountBig());
    }

    @Overwrite
    public boolean hasStoredAmount() {
        return this.getStoredAmountBig()
            .signum() > 0;
    }

    @Overwrite
    public boolean hasActiveAmount() {
        return this.getActiveAmountBig()
            .signum() > 0;
    }

    @Overwrite
    public boolean hasPendingAmount() {
        return this.getPendingAmountBig()
            .signum() > 0;
    }

    @Overwrite
    public long getTotalAmount() {
        return BigAEStackValues.saturatedLong(this.getTotalAmountBig());
    }

    @Overwrite
    public IAEStack<?> getVisualStack() {
        final CraftingCpuEntry self = (CraftingCpuEntry) (Object) this;
        final IAEStack<?> visualStack = self.getStack()
            .copy();
        return BigAEStackValues.set(visualStack, this.getTotalAmountBig());
    }
}
