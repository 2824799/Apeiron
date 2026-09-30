package com.silvia.apeiron.mixin.ae.reshuffle;

import java.math.BigInteger;
import java.util.Comparator;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.reshuffle.BigReshuffleItemChangeAccess;
import com.silvia.apeiron.ae.reshuffle.BigReshuffleReportAccess;
import com.silvia.apeiron.ae.reshuffle.BigReshuffleReportPackets;

import appeng.helpers.ReshuffleReport;
import appeng.helpers.ReshuffleReport.ItemChange;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import io.netty.buffer.ByteBuf;

/** Makes storage-reshuffle report totals and differences exact and packet-safe. */
@Mixin(value = ReshuffleReport.class, remap = false)
public abstract class ReshuffleReportMixin implements BigReshuffleReportAccess {

    @Shadow @Final public double extractedItems;
    @Shadow @Final public double injectedItems;
    @Shadow public double beforeItems;
    @Shadow public double afterItems;
    @Shadow @Final public java.util.List<ItemChange> lostItems;
    @Shadow @Final public java.util.List<ItemChange> gainedItems;
    @Shadow @Final public IItemList<IAEStack<?>> stackLookup;
    @Shadow @Final public IItemList<IAEStack<?>> beforeSnapshot;
    @Shadow @Final public IItemList<IAEStack<?>> afterSnapshot;

    @Unique private BigInteger apeiron$extracted;
    @Unique private BigInteger apeiron$injected;
    @Unique private BigInteger apeiron$before;
    @Unique private BigInteger apeiron$after;

    @Inject(method = "<init>(Lio/netty/buffer/ByteBuf;)V", at = @At("TAIL"))
    private void apeiron$readExact(final ByteBuf buf, final CallbackInfo ci) throws java.io.IOException {
        BigReshuffleReportPackets.read(buf, (ReshuffleReport) (Object) this);
    }

    @Inject(method = "writeToPacket", at = @At("TAIL"))
    private void apeiron$writeExact(final ByteBuf buf, final CallbackInfo ci) {
        BigReshuffleReportPackets.write(buf, (ReshuffleReport) (Object) this);
    }

    @Inject(method = "generateReport", at = @At("TAIL"))
    private void apeiron$rebuildExact(final CallbackInfo ci) {
        final ReshuffleReport self = (ReshuffleReport) (Object) this;
        if (self.stackLookup == null || self.beforeSnapshot == null || self.afterSnapshot == null) return;
        BigInteger beforeTotal = BigInteger.ZERO;
        BigInteger afterTotal = BigInteger.ZERO;
        self.lostItems.clear();
        self.gainedItems.clear();
        for (final IAEStack<?> lookup : self.stackLookup) {
            final IAEStack<?> before = self.beforeSnapshot.findPrecise(lookup);
            final IAEStack<?> after = self.afterSnapshot.findPrecise(lookup);
            final BigInteger beforeCount = BigAEStackValues.get(before);
            final BigInteger afterCount = BigAEStackValues.get(after);
            beforeTotal = beforeTotal.add(beforeCount);
            afterTotal = afterTotal.add(afterCount);
            if (beforeCount.equals(afterCount)) continue;
            final ItemChange change = new ItemChange(
                    lookup,
                    BigAEStackValues.saturatedLong(beforeCount),
                    BigAEStackValues.saturatedLong(afterCount));
            ((BigReshuffleItemChangeAccess) change).setCountsBig(beforeCount, afterCount);
            if (afterCount.compareTo(beforeCount) > 0) self.gainedItems.add(change);
            else self.lostItems.add(change);
        }
        this.apeiron$before = beforeTotal;
        this.apeiron$after = afterTotal;
        self.beforeItems = beforeTotal.doubleValue();
        self.afterItems = afterTotal.doubleValue();
        self.gainedItems.sort(ReshuffleReportMixin::compareChange);
        self.lostItems.sort(ReshuffleReportMixin::compareChange);
    }

    @Override public BigInteger getExtractedItemsBig() {
        return this.apeiron$extracted == null ? BigInteger.valueOf((long) this.extractedItems) : this.apeiron$extracted;
    }
    @Override public BigInteger getInjectedItemsBig() {
        return this.apeiron$injected == null ? BigInteger.valueOf((long) this.injectedItems) : this.apeiron$injected;
    }
    @Override public BigInteger getBeforeItemsBig() {
        return this.apeiron$before == null ? BigInteger.valueOf((long) this.beforeItems) : this.apeiron$before;
    }
    @Override public BigInteger getAfterItemsBig() {
        return this.apeiron$after == null ? BigInteger.valueOf((long) this.afterItems) : this.apeiron$after;
    }
    @Override public void setTransferTotalsBig(final BigInteger extracted, final BigInteger injected) {
        this.apeiron$extracted = extracted;
        this.apeiron$injected = injected;
    }

    @Unique
    private static int compareChange(final ItemChange left, final ItemChange right) {
        final BigInteger a = ((BigReshuffleItemChangeAccess) left).getDifferenceBig().abs();
        final BigInteger b = ((BigReshuffleItemChangeAccess) right).getDifferenceBig().abs();
        return b.compareTo(a);
    }
}
