package com.silvia.apeiron.mixin.ae.crafting.core;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.crafting.diagnostics.BigCraftingDiagnosticsValues;
import com.silvia.apeiron.ae.crafting.diagnostics.BigCraftingTimingRecord;
import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.me.cluster.implementations.CraftingCpuDiagnostics;

/** Stores diagnostics production quantities exactly while keeping AE's long accessors. */
@Mixin(value = CraftingCpuDiagnostics.CraftingTimingRecord.class, remap = false)
public abstract class CraftingTimingRecordMixin implements BigCraftingTimingRecord {

    @Unique
    private BigInteger apeiron$remaining;
    @Unique
    private BigInteger apeiron$original;

    @Inject(method = "<init>(JJLappeng/me/diagnostics/CraftingDiagnosticSessionId;)V", at = @At("TAIL"))
    private void apeiron$capture(final long toProduce, final long startTick,
        final appeng.me.diagnostics.CraftingDiagnosticSessionId session, final CallbackInfo ci) {
        this.apeiron$remaining = BigCraftingDiagnosticsValues.takeTiming(toProduce);
        this.apeiron$original = this.apeiron$remaining;
    }

    @Shadow
    protected abstract void setEndTick(long endTick);

    @Override
    public BigInteger getRemainingToProduceBig() {
        return this.apeiron$remaining == null
            ? BigInteger.valueOf(((CraftingCpuDiagnostics.CraftingTimingRecord) (Object) this).getRemainingToProduce())
            : this.apeiron$remaining;
    }

    @Override
    public BigInteger getOriginalToProduceBig() {
        return this.apeiron$original == null
            ? BigInteger.valueOf(((CraftingCpuDiagnostics.CraftingTimingRecord) (Object) this).getOriginalToProduce())
            : this.apeiron$original;
    }

    @Override
    public void addRemainingToProduceBig(final BigInteger delta) {
        this.apeiron$remaining = this.getRemainingToProduceBig()
            .add(delta);
    }

    @Override
    public void addProducedBig(final BigInteger delta) {
        this.apeiron$remaining = this.getRemainingToProduceBig()
            .add(delta);
        this.apeiron$original = this.getOriginalToProduceBig()
            .add(delta);
    }

    @Override
    public void setEndTickBig(final long endTick) {
        this.setEndTick(endTick);
    }

    @Overwrite
    protected void addRemainingToProduce(final long delta) {
        this.addRemainingToProduceBig(BigInteger.valueOf(delta));
    }

    @Overwrite
    protected void addProduced(final long delta) {
        this.addProducedBig(BigInteger.valueOf(delta));
    }

    @Overwrite
    public long getRemainingToProduce() {
        return BigAEStackValues.saturatedLong(this.getRemainingToProduceBig());
    }

    @Overwrite
    public long getOriginalToProduce() {
        return BigAEStackValues.saturatedLong(this.getOriginalToProduceBig());
    }
}
