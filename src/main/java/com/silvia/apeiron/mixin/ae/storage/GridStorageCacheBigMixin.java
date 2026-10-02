package com.silvia.apeiron.mixin.ae.storage;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.storage.BigCellInventoryHandler;
import com.silvia.apeiron.ae.storage.BigStorageGrid;

import appeng.api.storage.ICellCacheRegistry;
import appeng.api.storage.ICellProvider;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.me.cache.GridStorageCache;

/** Rebuilds network capacity statistics from exact cell-handler values after AE updates its legacy counters. */
@Mixin(value = GridStorageCache.class, remap = false)
public abstract class GridStorageCacheBigMixin implements BigStorageGrid {

    private static final BigInteger INTEGER_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    @Shadow
    @Final
    private HashSet<ICellProvider> activeCellProviders;

    @Unique
    private BigInteger apeiron$itemBytesTotal = BigInteger.ZERO;
    @Unique
    private BigInteger apeiron$itemBytesUsed = BigInteger.ZERO;
    @Unique
    private BigInteger apeiron$itemTypesTotal = BigInteger.ZERO;
    @Unique
    private BigInteger apeiron$itemTypesUsed = BigInteger.ZERO;
    @Unique
    private BigInteger apeiron$itemCellCount = BigInteger.ZERO;

    @Unique
    private BigInteger apeiron$fluidBytesTotal = BigInteger.ZERO;
    @Unique
    private BigInteger apeiron$fluidBytesUsed = BigInteger.ZERO;
    @Unique
    private BigInteger apeiron$fluidTypesTotal = BigInteger.ZERO;
    @Unique
    private BigInteger apeiron$fluidTypesUsed = BigInteger.ZERO;
    @Unique
    private BigInteger apeiron$fluidCellCount = BigInteger.ZERO;

    @Unique
    private BigInteger apeiron$essentiaBytesTotal = BigInteger.ZERO;
    @Unique
    private BigInteger apeiron$essentiaBytesUsed = BigInteger.ZERO;
    @Unique
    private BigInteger apeiron$essentiaTypesTotal = BigInteger.ZERO;
    @Unique
    private BigInteger apeiron$essentiaTypesUsed = BigInteger.ZERO;
    @Unique
    private BigInteger apeiron$essentiaCellCount = BigInteger.ZERO;

    @Inject(method = "updateBytesInfo", at = @At("TAIL"))
    private void apeiron$rebuildExactStatistics(final CallbackInfo ci) {
        this.apeiron$itemBytesTotal = BigInteger.ZERO;
        this.apeiron$itemBytesUsed = BigInteger.ZERO;
        this.apeiron$itemTypesTotal = BigInteger.ZERO;
        this.apeiron$itemTypesUsed = BigInteger.ZERO;
        this.apeiron$itemCellCount = BigInteger.ZERO;
        this.apeiron$fluidBytesTotal = BigInteger.ZERO;
        this.apeiron$fluidBytesUsed = BigInteger.ZERO;
        this.apeiron$fluidTypesTotal = BigInteger.ZERO;
        this.apeiron$fluidTypesUsed = BigInteger.ZERO;
        this.apeiron$fluidCellCount = BigInteger.ZERO;
        this.apeiron$essentiaBytesTotal = BigInteger.ZERO;
        this.apeiron$essentiaBytesUsed = BigInteger.ZERO;
        this.apeiron$essentiaTypesTotal = BigInteger.ZERO;
        this.apeiron$essentiaTypesUsed = BigInteger.ZERO;
        this.apeiron$essentiaCellCount = BigInteger.ZERO;

        final Map<ICellCacheRegistry, Boolean> seen = new IdentityHashMap<>();
        for (final ICellProvider provider : this.activeCellProviders) {
            for (final IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                final List<IMEInventoryHandler> handlers = provider.getCellArray(type);
                if (handlers == null) continue;
                for (final IMEInventoryHandler handler : handlers) {
                    final ICellCacheRegistry registry = apeiron$findRegistry(handler);
                    if (registry == null || seen.put(registry, Boolean.TRUE) != null || !registry.canGetInv()) continue;
                    apeiron$add(registry);
                }
            }
        }
    }

    @Unique
    private static ICellCacheRegistry apeiron$findRegistry(final IMEInventoryHandler handler) {
        Object current = handler;
        for (int depth = 0; depth < 4 && current != null; depth++) {
            if (current instanceof ICellCacheRegistry) return (ICellCacheRegistry) current;
            if (!(current instanceof IMEInventoryHandler)) break;
            current = ((IMEInventoryHandler) current).getInternal();
        }
        return null;
    }

    @Unique
    private void apeiron$add(final ICellCacheRegistry registry) {
        final BigCellInventoryHandler exact = registry instanceof BigCellInventoryHandler
            ? (BigCellInventoryHandler) registry
            : null;

        final BigInteger totalBytes = exact == null ? BigInteger.valueOf(registry.getTotalBytes())
            : exact.getTotalBytesBig();
        final BigInteger usedBytes = exact == null ? BigInteger.valueOf(registry.getUsedBytes())
            : exact.getUsedBytesBig();
        BigInteger totalTypes = exact == null ? BigInteger.valueOf(registry.getTotalTypes()) : exact.getTotalTypesBig();
        BigInteger usedTypes = exact == null ? BigInteger.valueOf(registry.getUsedTypes()) : exact.getUsedTypesBig();

        // AE uses these values for void cells and deliberately excludes them from the finite type totals.
        if (totalTypes.equals(INTEGER_MAX) || totalTypes.equals(LONG_MAX)) {
            totalTypes = BigInteger.ZERO;
            usedTypes = BigInteger.ZERO;
        }

        switch (registry.getCellType()) {
            case ITEM:
                this.apeiron$itemBytesTotal = this.apeiron$itemBytesTotal.add(totalBytes);
                this.apeiron$itemBytesUsed = this.apeiron$itemBytesUsed.add(usedBytes);
                this.apeiron$itemTypesTotal = this.apeiron$itemTypesTotal.add(totalTypes);
                this.apeiron$itemTypesUsed = this.apeiron$itemTypesUsed.add(usedTypes);
                this.apeiron$itemCellCount = this.apeiron$itemCellCount.add(BigInteger.ONE);
                break;
            case FLUID:
                this.apeiron$fluidBytesTotal = this.apeiron$fluidBytesTotal.add(totalBytes);
                this.apeiron$fluidBytesUsed = this.apeiron$fluidBytesUsed.add(usedBytes);
                this.apeiron$fluidTypesTotal = this.apeiron$fluidTypesTotal.add(totalTypes);
                this.apeiron$fluidTypesUsed = this.apeiron$fluidTypesUsed.add(usedTypes);
                this.apeiron$fluidCellCount = this.apeiron$fluidCellCount.add(BigInteger.ONE);
                break;
            case ESSENTIA:
                this.apeiron$essentiaBytesTotal = this.apeiron$essentiaBytesTotal.add(totalBytes);
                this.apeiron$essentiaBytesUsed = this.apeiron$essentiaBytesUsed.add(usedBytes);
                this.apeiron$essentiaTypesTotal = this.apeiron$essentiaTypesTotal.add(totalTypes);
                this.apeiron$essentiaTypesUsed = this.apeiron$essentiaTypesUsed.add(usedTypes);
                this.apeiron$essentiaCellCount = this.apeiron$essentiaCellCount.add(BigInteger.ONE);
                break;
            default:
                break;
        }
    }

    @Override
    public BigInteger getItemBytesTotalBig() {
        return this.apeiron$itemBytesTotal;
    }

    @Override
    public BigInteger getItemBytesUsedBig() {
        return this.apeiron$itemBytesUsed;
    }

    @Override
    public BigInteger getItemTypesTotalBig() {
        return this.apeiron$itemTypesTotal;
    }

    @Override
    public BigInteger getItemTypesUsedBig() {
        return this.apeiron$itemTypesUsed;
    }

    @Override
    public BigInteger getItemCellCountBig() {
        return this.apeiron$itemCellCount;
    }

    @Override
    public BigInteger getFluidBytesTotalBig() {
        return this.apeiron$fluidBytesTotal;
    }

    @Override
    public BigInteger getFluidBytesUsedBig() {
        return this.apeiron$fluidBytesUsed;
    }

    @Override
    public BigInteger getFluidTypesTotalBig() {
        return this.apeiron$fluidTypesTotal;
    }

    @Override
    public BigInteger getFluidTypesUsedBig() {
        return this.apeiron$fluidTypesUsed;
    }

    @Override
    public BigInteger getFluidCellCountBig() {
        return this.apeiron$fluidCellCount;
    }

    @Override
    public BigInteger getEssentiaBytesTotalBig() {
        return this.apeiron$essentiaBytesTotal;
    }

    @Override
    public BigInteger getEssentiaBytesUsedBig() {
        return this.apeiron$essentiaBytesUsed;
    }

    @Override
    public BigInteger getEssentiaTypesTotalBig() {
        return this.apeiron$essentiaTypesTotal;
    }

    @Override
    public BigInteger getEssentiaTypesUsedBig() {
        return this.apeiron$essentiaTypesUsed;
    }

    @Override
    public BigInteger getEssentiaCellCountBig() {
        return this.apeiron$essentiaCellCount;
    }
}
