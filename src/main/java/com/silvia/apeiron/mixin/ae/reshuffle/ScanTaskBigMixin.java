package com.silvia.apeiron.mixin.ae.reshuffle;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.reshuffle.BigScanRecord;
import com.silvia.apeiron.ae.reshuffle.BigScanRecordValues;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigCellInventory;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.core.AELog;
import appeng.helpers.ScanTask;
import appeng.me.storage.CellInventory;
import appeng.util.IterationCounter;
import appeng.util.item.IAEStackList;
import io.netty.buffer.ByteBuf;

/** Sorts storage-scan preview entries by their exact amount. */
@Mixin(value = ScanTask.class, remap = false)
public abstract class ScanTaskBigMixin {

    private static final int APEIRON_SCAN_MAGIC = 0x41505343;

    @Shadow
    @Final
    private Map<appeng.api.storage.data.IAEStackType<?>, Map<IAEStack<?>, List<ScanTask.ScanRecord>>> scanData;

    @Shadow
    @Final
    private List<ScanTask.ScanRecord> scanCellsData;

    @Overwrite
    private static List<IAEStack<?>> collectTopStoredItems(final IMEInventory<?> cellInv, final int maxItems) {
        final List<IAEStack<?>> entries = new ArrayList<>();
        try {
            final List<IAEStack<?>> allStacks = new ArrayList<>();
            cellInv.getAvailableItems((IItemList) new IAEStackList(), IterationCounter.fetchNewId())
                .forEach(stack -> allStacks.add((IAEStack<?>) stack));
            allStacks.sort(
                Comparator.comparing((IAEStack<?> stack) -> BigAEStackValues.get(stack))
                    .reversed());
            for (int i = 0; i < Math.min(maxItems, allStacks.size()); i++) {
                entries.add(allStacks.get(i));
            }
        } catch (final Exception e) {
            AELog.debug(e);
        }
        return entries;
    }

    @Redirect(
        method = "scanGrid",
        at = @At(value = "INVOKE", target = "Lappeng/me/storage/CellInventory;getStoredItemTypes()J"))
    private long apeiron$captureTypesUsed(final CellInventory<?> inventory) {
        final long legacy = inventory.getStoredItemTypes();
        final BigInteger[] current = BigScanRecordValues.current();
        final BigInteger total = current == null ? BigInteger.valueOf(inventory.getTotalItemTypes()) : current[1];
        final BigInteger bytesUsed = current == null ? BigInteger.valueOf(inventory.getUsedBytes()) : current[2];
        final BigInteger bytesTotal = current == null ? BigInteger.valueOf(inventory.getTotalBytes()) : current[3];
        BigScanRecordValues.capture(BigInteger.valueOf(legacy), total, bytesUsed, bytesTotal);
        return legacy;
    }

    @Redirect(
        method = "scanGrid",
        at = @At(value = "INVOKE", target = "Lappeng/me/storage/CellInventory;getTotalItemTypes()J"))
    private long apeiron$captureTypesTotal(final CellInventory<?> inventory) {
        final long legacy = inventory.getTotalItemTypes();
        final BigInteger[] current = BigScanRecordValues.current();
        BigScanRecordValues.capture(
            current == null ? BigInteger.valueOf(inventory.getStoredItemTypes()) : current[0],
            BigInteger.valueOf(legacy),
            current == null ? BigInteger.valueOf(inventory.getUsedBytes()) : current[2],
            current == null ? BigInteger.valueOf(inventory.getTotalBytes()) : current[3]);
        return legacy;
    }

    @Redirect(
        method = "scanGrid",
        at = @At(value = "INVOKE", target = "Lappeng/me/storage/CellInventory;getUsedBytes()J"))
    private long apeiron$captureBytesUsed(final CellInventory<?> inventory) {
        final long legacy = inventory.getUsedBytes();
        final BigInteger exact = inventory instanceof BigCellInventory
            ? ((BigCellInventory) (Object) inventory).getUsedBytesBig()
            : BigInteger.valueOf(legacy);
        final BigInteger[] current = BigScanRecordValues.current();
        BigScanRecordValues.capture(
            current == null ? BigInteger.valueOf(inventory.getStoredItemTypes()) : current[0],
            current == null ? BigInteger.valueOf(inventory.getTotalItemTypes()) : current[1],
            exact,
            current == null ? BigInteger.valueOf(inventory.getTotalBytes()) : current[3]);
        return BigAEStackValues.saturatedLong(exact);
    }

    @Redirect(
        method = "scanGrid",
        at = @At(value = "INVOKE", target = "Lappeng/me/storage/CellInventory;getTotalBytes()J"))
    private long apeiron$captureBytesTotal(final CellInventory<?> inventory) {
        final long legacy = inventory.getTotalBytes();
        final BigInteger exact = inventory instanceof BigCellInventory
            ? ((BigCellInventory) (Object) inventory).getTotalBytesBig()
            : BigInteger.valueOf(legacy);
        final BigInteger[] current = BigScanRecordValues.current();
        BigScanRecordValues.capture(
            current == null ? BigInteger.valueOf(inventory.getStoredItemTypes()) : current[0],
            current == null ? BigInteger.valueOf(inventory.getTotalItemTypes()) : current[1],
            current == null ? BigInteger.valueOf(inventory.getUsedBytes()) : current[2],
            exact);
        return BigAEStackValues.saturatedLong(exact);
    }

    @Inject(method = "scan", at = @At("RETURN"))
    private void apeiron$clearScanValues(final net.minecraft.item.ItemStack is, final IMEInventory<?> inv,
        final appeng.api.storage.data.IAEStackType<?> type, final int slot,
        final appeng.tile.inventory.IAEStackInventory configInv, final appeng.api.util.DimensionalCoord dc,
        final long typesUsed, final long typesTotal, final long bytesUsed, final long bytesTotal,
        final CallbackInfo ci) {
        BigScanRecordValues.clear();
    }

    @Inject(method = "writeToPacket", at = @At("TAIL"))
    private void apeiron$writeExact(final ByteBuf buf, final CallbackInfo ci) {
        final List<ScanTask.ScanRecord> records = this.apeiron$records();
        boolean hasBig = false;
        for (final ScanTask.ScanRecord record : records) {
            if ((Object) record instanceof BigScanRecord exact && exact.hasBigScanValues()) {
                hasBig = true;
                break;
            }
        }
        if (!hasBig) return;
        buf.writeInt(APEIRON_SCAN_MAGIC);
        buf.writeInt(records.size());
        for (final ScanTask.ScanRecord record : records) {
            final BigScanRecord exact = (Object) record instanceof BigScanRecord ? (BigScanRecord) (Object) record
                : null;
            buf.writeInt(record.slot);
            buf.writeInt(record.x);
            buf.writeInt(record.y);
            buf.writeInt(record.z);
            buf.writeInt(record.dim);
            BigValueCodec
                .writePacket(buf, exact == null ? BigInteger.valueOf(record.typesUsed) : exact.getTypesUsedBig());
            BigValueCodec
                .writePacket(buf, exact == null ? BigInteger.valueOf(record.typesTotal) : exact.getTypesTotalBig());
            BigValueCodec
                .writePacket(buf, exact == null ? BigInteger.valueOf(record.bytesUsed) : exact.getBytesUsedBig());
            BigValueCodec
                .writePacket(buf, exact == null ? BigInteger.valueOf(record.bytesTotal) : exact.getBytesTotalBig());
        }
    }

    @Inject(method = "<init>(Lio/netty/buffer/ByteBuf;)V", at = @At("TAIL"))
    private void apeiron$readExact(final ByteBuf buf, final CallbackInfo ci) throws IOException {
        if (buf.readableBytes() < Integer.BYTES * 2 || buf.getInt(buf.readerIndex()) != APEIRON_SCAN_MAGIC) return;
        buf.skipBytes(Integer.BYTES);
        final int count = buf.readInt();
        for (int i = 0; i < count; i++) {
            final int slot = buf.readInt();
            final int x = buf.readInt();
            final int y = buf.readInt();
            final int z = buf.readInt();
            final int dim = buf.readInt();
            final BigInteger typesUsed = BigValueCodec.readPacket(buf);
            final BigInteger typesTotal = BigValueCodec.readPacket(buf);
            final BigInteger bytesUsed = BigValueCodec.readPacket(buf);
            final BigInteger bytesTotal = BigValueCodec.readPacket(buf);
            for (final ScanTask.ScanRecord record : this.apeiron$records()) {
                if (record.slot == slot && record.x == x
                    && record.y == y
                    && record.z == z
                    && record.dim == dim
                    && (Object) record instanceof BigScanRecord exact) {
                    exact.setExactScanValues(typesUsed, typesTotal, bytesUsed, bytesTotal);
                    break;
                }
            }
        }
    }

    private List<ScanTask.ScanRecord> apeiron$records() {
        final List<ScanTask.ScanRecord> records = new ArrayList<>();
        for (final Map<IAEStack<?>, List<ScanTask.ScanRecord>> partitions : this.scanData.values()) {
            for (final List<ScanTask.ScanRecord> entries : partitions.values()) records.addAll(entries);
        }
        records.addAll(this.scanCellsData);
        return records;
    }
}
