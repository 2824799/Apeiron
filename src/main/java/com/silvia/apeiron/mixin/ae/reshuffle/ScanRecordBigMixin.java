package com.silvia.apeiron.mixin.ae.reshuffle;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.reshuffle.BigScanRecord;
import com.silvia.apeiron.ae.reshuffle.BigScanRecordValues;

import appeng.helpers.ScanTask;

/** Keeps ScanTask cell health values exact while preserving its long fields. */
@Mixin(value = ScanTask.ScanRecord.class, remap = false)
public abstract class ScanRecordBigMixin implements BigScanRecord {

    @Unique
    private BigInteger apeiron$typesUsed;
    @Unique
    private BigInteger apeiron$typesTotal;
    @Unique
    private BigInteger apeiron$bytesUsed;
    @Unique
    private BigInteger apeiron$bytesTotal;

    @Inject(method = "<init>(ILnet/minecraft/item/ItemStack;JJJJLappeng/api/util/DimensionalCoord;Ljava/util/List;)V", at = @At("TAIL"))
    private void apeiron$capture(final int slot, final net.minecraft.item.ItemStack itemStack, final long typesUsed,
        final long typesTotal, final long bytesUsed, final long bytesTotal,
        final appeng.api.util.DimensionalCoord dc, final java.util.List<?> topStoredItems, final CallbackInfo ci) {
        final BigInteger[] exact = BigScanRecordValues.current();
        this.apeiron$typesUsed = exact == null ? BigInteger.valueOf(typesUsed) : exact[0];
        this.apeiron$typesTotal = exact == null ? BigInteger.valueOf(typesTotal) : exact[1];
        this.apeiron$bytesUsed = exact == null ? BigInteger.valueOf(bytesUsed) : exact[2];
        this.apeiron$bytesTotal = exact == null ? BigInteger.valueOf(bytesTotal) : exact[3];
    }

    @Override
    public BigInteger getTypesUsedBig() {
        return this.apeiron$typesUsed == null
            ? BigInteger.valueOf(((ScanTask.ScanRecord) (Object) this).typesUsed)
            : this.apeiron$typesUsed;
    }

    @Override
    public BigInteger getTypesTotalBig() {
        return this.apeiron$typesTotal == null
            ? BigInteger.valueOf(((ScanTask.ScanRecord) (Object) this).typesTotal)
            : this.apeiron$typesTotal;
    }

    @Override
    public BigInteger getBytesUsedBig() {
        return this.apeiron$bytesUsed == null
            ? BigInteger.valueOf(((ScanTask.ScanRecord) (Object) this).bytesUsed)
            : this.apeiron$bytesUsed;
    }

    @Override
    public BigInteger getBytesTotalBig() {
        return this.apeiron$bytesTotal == null
            ? BigInteger.valueOf(((ScanTask.ScanRecord) (Object) this).bytesTotal)
            : this.apeiron$bytesTotal;
    }

    @Override
    public boolean hasBigScanValues() {
        return !BigAEStackValues.fitsLong(this.getTypesUsedBig())
            || !BigAEStackValues.fitsLong(this.getTypesTotalBig())
            || !BigAEStackValues.fitsLong(this.getBytesUsedBig())
            || !BigAEStackValues.fitsLong(this.getBytesTotalBig());
    }

    @Override
    public void setExactScanValues(final BigInteger typesUsed, final BigInteger typesTotal,
        final BigInteger bytesUsed, final BigInteger bytesTotal) {
        this.apeiron$typesUsed = typesUsed;
        this.apeiron$typesTotal = typesTotal;
        this.apeiron$bytesUsed = bytesUsed;
        this.apeiron$bytesTotal = bytesTotal;
    }
}
