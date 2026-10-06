package com.silvia.apeiron.mixin.ae.storage;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.silvia.apeiron.ae.storage.StorageCellMounts;

import appeng.api.storage.ICellProvider;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEStackType;
import appeng.me.cache.GridStorageCache;

/** Multiple integrations may mount the same cell channel; count and transfer it once. */
@Mixin(value = GridStorageCache.class, remap = false)
public abstract class CellProviderMountsMixin {

    @SuppressWarnings("rawtypes")
    @Redirect(
        method = { "addCellProvider", "removeCellProvider", "buildNetworkStorage" },
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/ICellProvider;getCellArray(Lappeng/api/storage/data/IAEStackType;)Ljava/util/List;"),
        require = 3,
        allow = 3)
    private List<IMEInventoryHandler> apeiron$uniqueCellMounts(final ICellProvider provider,
        final IAEStackType<?> type) {
        return StorageCellMounts.unique(provider.getCellArray(type));
    }
}
